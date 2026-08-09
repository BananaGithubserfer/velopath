import argparse
import asyncio
import os
import subprocess
import sys
from pathlib import Path

from bleak import BleakClient, BleakScanner
from bleak.backends.device import BLEDevice
from bleak.backends.winrt.client import (
    BluetoothCacheMode,
    BluetoothLEDevice,
    Buffer,
    GattClientCharacteristicConfigurationDescriptorValue,
    GattWriteOption,
)
from bleak.backends.winrt.util import uninitialize_sta


TARGET_NAMES = [
    name.strip()
    for name in os.environ.get("BLE_TARGET_NAMES", "VeloPath,WH-BLE 106").split(",")
    if name.strip()
]
TARGET_NAME_KEYS = [name.lower() for name in TARGET_NAMES]
TARGET_ADDRESS = os.environ.get("BLE_TARGET_ADDRESS", "").strip().upper()
SERVICE_UUID = "0003cdd0-0000-1000-8000-00805f9b0131"
NOTIFY_UUID = "0003cdd1-0000-1000-8000-00805f9b0131"
WRITE_UUID = "0003cdd2-0000-1000-8000-00805f9b0131"
PROMPT_TOKEN = b"> "
ASCII_STREAM_ONLY = os.environ.get("BLE_STREAM_ASCII_ONLY", "").strip() == "1"
LINE_FILTER_PREFIX = os.environ.get("BLE_STREAM_LINE_FILTER", "").strip()
def sanitize_notify_bytes(data: bytes) -> bytes:
    if not ASCII_STREAM_ONLY:
        return data

    allowed = bytearray()
    for byte in data:
        if byte in (0x0A, 0x0D, 0x09) or 0x20 <= byte <= 0x7E:
            allowed.append(byte)
    return bytes(allowed)


def filter_notify_text(text: str) -> str:
    if not LINE_FILTER_PREFIX:
        return text

    filtered_lines: list[str] = []
    for line in text.splitlines(keepends=True):
        stripped = line.lstrip()
        if stripped.startswith(LINE_FILTER_PREFIX):
            filtered_lines.append(line)
    return "".join(filtered_lines)


def prepare_windows_ble() -> None:
    # Bleak recommends undoing accidental STA initialization for console apps.
    uninitialize_sta()


def run_pair_script(script_name: str) -> None:
    script_path = Path(__file__).with_name(script_name)
    completed = subprocess.run(
        [
            "powershell",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            str(script_path),
        ],
        capture_output=True,
        text=True,
        check=False,
    )
    print(completed.stdout.strip())
    if completed.stderr.strip():
        print(completed.stderr.strip(), file=sys.stderr)


async def find_target() -> BLEDevice:
    devices = await BleakScanner.discover(timeout=6.0, return_adv=True)
    for _, pair in devices.items():
        device, adv = pair
        name = adv.local_name or device.name or ""
        name_key = name.lower()
        service_uuids = [uuid.lower() for uuid in (adv.service_uuids or [])]

        if TARGET_ADDRESS and device.address.upper() == TARGET_ADDRESS:
            return device
        if name in TARGET_NAMES:
            return device
        if any(key in name_key for key in TARGET_NAME_KEYS):
            return device
        if SERVICE_UUID in service_uuids:
            return device
    raise RuntimeError(f"BLE device '{TARGET_NAMES[0]}' not found")


class WinRtConsoleSession:
    def __init__(self, device: BLEDevice) -> None:
        self.device = device
        self.requester = None
        self.service = None
        self.notify_char = None
        self.write_char = None
        self.notify_token = None
        self.rx_buffer = bytearray()

    async def connect(self) -> None:
        address = int(self.device.address.replace(":", ""), 16)
        self.requester = await BluetoothLEDevice.from_bluetooth_address_async(address)
        if self.requester is None:
            raise RuntimeError("BluetoothLEDevice.from_bluetooth_address_async returned None")

        # Give the WinRT stack time to settle before service discovery.
        await asyncio.sleep(0.6)

        service_result = await self.requester.get_gatt_services_with_cache_mode_async(
            BluetoothCacheMode.CACHED
        )
        if int(service_result.status) != 0:
            # Retry with UNCACHED; WinRT sometimes returns stale/invalid cache.
            service_result = await self.requester.get_gatt_services_with_cache_mode_async(
                BluetoothCacheMode.UNCACHED
            )
            if int(service_result.status) != 0:
                raise RuntimeError(f"GetGattServices status={service_result.status}")

        services = list(service_result.services)
        target_services = [svc for svc in services if str(svc.uuid).lower() == SERVICE_UUID]
        if not target_services:
            target_services = services

        # Ensure connection is established before characteristic discovery.
        if target_services:
            session = target_services[0].session
            session.maintain_connection = True

        for _ in range(20):
            await asyncio.sleep(0.5)
            if int(self.requester.connection_status) == 1:
                break
        else:
            raise RuntimeError("Device never reached connected state")

        def select_chars(char_result) -> bool:
            for characteristic in char_result.characteristics:
                uuid = str(characteristic.uuid).lower()
                if uuid == NOTIFY_UUID:
                    self.notify_char = characteristic
                elif uuid == WRITE_UUID:
                    self.write_char = characteristic

                props = characteristic.characteristic_properties
                if self.notify_char is None and (
                    getattr(props, "notify", False) or getattr(props, "indicate", False)
                ):
                    self.notify_char = characteristic
                if self.write_char is None and (
                    getattr(props, "write_without_response", False) or getattr(props, "write", False)
                ):
                    self.write_char = characteristic
            return self.notify_char is not None and self.write_char is not None

        for service in target_services:
            char_result = await service.get_characteristics_with_cache_mode_async(
                BluetoothCacheMode.CACHED
            )
            if int(char_result.status) != 0:
                await asyncio.sleep(0.5)
                char_result = await service.get_characteristics_with_cache_mode_async(
                    BluetoothCacheMode.UNCACHED
                )
                if int(char_result.status) != 0:
                    await asyncio.sleep(0.8)
                    char_result = await service.get_characteristics_with_cache_mode_async(
                        BluetoothCacheMode.UNCACHED
                    )
            if int(char_result.status) == 0 and select_chars(char_result):
                self.service = service
                break

        if self.service is None:
            # Retry with fully uncached service discovery.
            service_result = await self.requester.get_gatt_services_with_cache_mode_async(
                BluetoothCacheMode.UNCACHED
            )
            if int(service_result.status) == 0:
                for service in service_result.services:
                    char_result = await service.get_characteristics_with_cache_mode_async(
                        BluetoothCacheMode.UNCACHED
                    )
                    if int(char_result.status) == 0 and select_chars(char_result):
                        self.service = service
                        break

        if self.notify_char is None or self.write_char is None:
            raise RuntimeError("Required characteristics not found")

        def on_notify(sender, args) -> None:  # noqa: ANN001
            data = sanitize_notify_bytes(bytes(args.characteristic_value))
            if not data:
                return
            text = data.decode("utf-8", errors="replace")
            text = filter_notify_text(text)
            if not text:
                return
            encoded = text.encode("utf-8", errors="replace")
            self.rx_buffer.extend(encoded)
            sys.stdout.write(text)
            sys.stdout.flush()

        self.notify_token = self.notify_char.add_value_changed(on_notify)
        notify_result = (
            await self.notify_char.write_client_characteristic_configuration_descriptor_with_result_async(
                GattClientCharacteristicConfigurationDescriptorValue.NOTIFY
            )
        )
        if int(notify_result.status) != 0:
            raise RuntimeError(f"Enable notify status={notify_result.status}")

    async def disconnect(self) -> None:
        if self.notify_char is not None and self.notify_token is not None:
            try:
                self.notify_char.remove_value_changed(self.notify_token)
            except Exception:  # noqa: BLE001
                pass
        self.notify_token = None
        self.notify_char = None
        self.write_char = None
        self.service = None
        self.requester = None

    async def send_line(self, line: str) -> None:
        payload = (line.rstrip("\r\n") + "\r\n").encode("utf-8")
        buf = Buffer(len(payload))
        buf.length = len(payload)
        memoryview(buf)[:] = payload
        result = await self.write_char.write_value_with_result_and_option_async(  # type: ignore[union-attr]
            buf,
            GattWriteOption.WRITE_WITHOUT_RESPONSE,
        )
        if int(result.status) != 0:
            raise RuntimeError(f"Write status={result.status}")


class BleakConsoleSession:
    def __init__(self, device: BLEDevice) -> None:
        self.device = device
        self.client: BleakClient | None = None
        self.rx_buffer = bytearray()

    async def connect(self) -> None:
        self.client = BleakClient(self.device, timeout=20.0, winrt={"use_cached_services": False})
        await self.client.connect()
        if hasattr(self.client, "get_services"):
            services = await self.client.get_services()
        else:
            services = self.client.services

        notify_uuid = NOTIFY_UUID
        write_uuid = WRITE_UUID

        if services.get_characteristic(NOTIFY_UUID) is None or services.get_characteristic(WRITE_UUID) is None:
            for service in services:
                for char in service.characteristics:
                    if notify_uuid == NOTIFY_UUID and ("notify" in char.properties or "indicate" in char.properties):
                        notify_uuid = char.uuid
                    if write_uuid == WRITE_UUID and (
                        "write_without_response" in char.properties or "write" in char.properties
                    ):
                        write_uuid = char.uuid
                if notify_uuid != NOTIFY_UUID and write_uuid != WRITE_UUID:
                    break

        self._notify_uuid = notify_uuid
        self._write_uuid = write_uuid

        async def handle_notify(_: int, data: bytearray) -> None:
            payload = sanitize_notify_bytes(bytes(data))
            if not payload:
                return
            text = payload.decode("utf-8", errors="replace")
            text = filter_notify_text(text)
            if not text:
                return
            encoded = text.encode("utf-8", errors="replace")
            self.rx_buffer.extend(encoded)
            sys.stdout.write(text)
            sys.stdout.flush()

        # Start notify with retries; services may not be ready right after connect.
        last_err: Exception | None = None
        for _ in range(3):
            try:
                await self.client.start_notify(self._notify_uuid, handle_notify)
                last_err = None
                break
            except Exception as exc:  # noqa: BLE001
                last_err = exc
                await asyncio.sleep(0.8)
        if last_err is not None:
            raise last_err

    async def disconnect(self) -> None:
        if self.client is not None:
            try:
                await self.client.stop_notify(self._notify_uuid)
            except Exception:  # noqa: BLE001
                pass
            await self.client.disconnect()
            self.client = None

    async def send_line(self, line: str) -> None:
        if self.client is None:
            raise RuntimeError("Bleak client not connected")
        payload = (line.rstrip("\r\n") + "\r\n").encode("utf-8")
        await self.client.write_gatt_char(self._write_uuid, payload, response=False)


async def wait_for_target(timeout_s: float = 20.0) -> bool:
    started = asyncio.get_running_loop().time()
    while asyncio.get_running_loop().time() - started < timeout_s:
        try:
            await find_target()
            return True
        except Exception:  # noqa: BLE001
            await asyncio.sleep(1.0)
    return False


async def repair_pair_cycle() -> None:
    run_pair_script("winrt_ble_repair.ps1")
    await asyncio.sleep(3.0)
    await wait_for_target(timeout_s=20.0)
    run_pair_script("winrt_ble_pair.ps1")
    await asyncio.sleep(3.0)
    await wait_for_target(timeout_s=20.0)


async def open_session(timeout_s: float = 8.0):
    device = await find_target()
    print(f"FOUND {TARGET_NAMES[0]} {device.address}")
    last_error: Exception | None = None
    for _ in range(1):
        session = WinRtConsoleSession(device)
        try:
            await asyncio.wait_for(session.connect(), timeout=max(timeout_s, 8.0))
            print("CONNECTED native-winrt")
            print("\nNOTIFY-ON")
            return session, session.rx_buffer
        except Exception as exc:  # noqa: BLE001
            last_error = exc
            await session.disconnect()
            await asyncio.sleep(0.8)

    # Fallback to BleakClient path with uncached services.
    try:
        session2 = BleakConsoleSession(device)
        await asyncio.wait_for(session2.connect(), timeout=max(timeout_s, 8.0))
        print("CONNECTED bleak-client")
        print("\nNOTIFY-ON")
        return session2, session2.rx_buffer
    except Exception as exc:  # noqa: BLE001
        last_error = exc

    raise RuntimeError(f"Unable to open BLE session: {last_error}")


async def send_line(client: WinRtConsoleSession, line: str) -> None:
    await client.send_line(line)


async def wait_for_prompt(rx_buffer: bytearray, timeout_s: float = 2.0) -> bool:
    started = asyncio.get_running_loop().time()
    initial_size = len(rx_buffer)
    while asyncio.get_running_loop().time() - started < timeout_s:
        if len(rx_buffer) > initial_size and PROMPT_TOKEN in rx_buffer[initial_size:]:
            return True
        await asyncio.sleep(0.05)
    return False


async def probe_terminal(client: WinRtConsoleSession, rx_buffer: bytearray) -> bool:
    # Let the BLE module finish any startup/AT chatter before probing.
    await asyncio.sleep(1.5)
    rx_buffer.clear()

    for _ in range(3):
        before = len(rx_buffer)
        await send_line(client, "ping")
        ok = await wait_for_prompt(rx_buffer, timeout_s=2.5)
        chunk = bytes(rx_buffer[before:])
        if ok and b"pong" in chunk:
            return True
        await asyncio.sleep(0.8)
    return False


async def interactive_terminal(client: WinRtConsoleSession, rx_buffer: bytearray) -> None:
    print("TERMINAL READY. Type commands, 'exit' to quit.")
    await send_line(client, "help")
    await wait_for_prompt(rx_buffer, timeout_s=2.0)

    while True:
        line = await asyncio.to_thread(input, "")
        if line.strip().lower() in {"exit", "quit"}:
            break
        await send_line(client, line)
        await wait_for_prompt(rx_buffer, timeout_s=2.0)


async def scripted_terminal(client: WinRtConsoleSession, rx_buffer: bytearray, commands: list[str]) -> None:
    print("SCRIPT MODE")
    for command in commands:
        print(f"\n>> {command}")
        await send_line(client, command)
        await wait_for_prompt(rx_buffer, timeout_s=2.5)


async def main() -> None:
    prepare_windows_ble()
    parser = argparse.ArgumentParser()
    parser.add_argument("--retries", type=int, default=4)
    parser.add_argument("--pair-reset-on-fail", action="store_true")
    parser.add_argument("--non-interactive", action="store_true")
    parser.add_argument("--command", action="append", default=[])
    parser.add_argument("--max-runtime", type=float, default=0.0)
    args = parser.parse_args()

    last_error: Exception | None = None

    async def run_attempts() -> None:
        nonlocal last_error
        for attempt in range(1, args.retries + 1):
            if attempt > 1 and args.pair_reset_on_fail:
                print(f"\nREPAIR ATTEMPT {attempt}")
                await repair_pair_cycle()

            client: WinRtConsoleSession | None = None
            try:
                try:
                    client, rx_buffer = await asyncio.wait_for(open_session(), timeout=20.0)
                except asyncio.TimeoutError as exc:
                    raise RuntimeError("open_session timeout") from exc
                if not await probe_terminal(client, rx_buffer):
                    raise RuntimeError("Probe command did not yield terminal prompt")

                print("\nPROBE OK")
                if args.command:
                    await scripted_terminal(client, rx_buffer, args.command)
                elif not args.non_interactive:
                    await interactive_terminal(client, rx_buffer)
                await client.disconnect()
                return
            except Exception as exc:  # noqa: BLE001
                last_error = exc
                print(f"\nATTEMPT {attempt} FAILED: {exc}")
                if client is not None:
                    try:
                        await client.disconnect()
                    except Exception:  # noqa: BLE001
                        pass
                await asyncio.sleep(1.5)

        raise SystemExit(f"Failed to stabilize BLE terminal: {last_error}")

    if args.max_runtime and args.max_runtime > 0:
        try:
            await asyncio.wait_for(run_attempts(), timeout=args.max_runtime)
        except asyncio.TimeoutError:
            raise SystemExit(f"Timed out after {args.max_runtime:.1f}s")
    else:
        await run_attempts()


if __name__ == "__main__":
    asyncio.run(main())
