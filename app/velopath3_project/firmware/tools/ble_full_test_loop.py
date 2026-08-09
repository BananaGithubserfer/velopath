import asyncio
import time
from typing import Optional

from bleak import BleakClient, BleakScanner

TARGET_NAME = "WH-BLE 106"
NOTIFY_UUID = "0003cdd1-0000-1000-8000-00805f9b0131"
WRITE_UUID = "0003cdd2-0000-1000-8000-00805f9b0131"
PROMPT_TOKEN = b"> "


async def find_target(timeout_s: float = 6.0):
    devices = await BleakScanner.discover(timeout=timeout_s, return_adv=True)
    for _, pair in devices.items():
        device, adv = pair
        name = adv.local_name or device.name or ""
        if name == TARGET_NAME:
            return device
    return None


async def run_full_test_once() -> bool:
    device = await find_target()
    if device is None:
        print("SCAN: target not found")
        return False

    print(f"SCAN: found {device.address}")
    client = BleakClient(device, timeout=12.0, winrt={"use_cached_services": False})
    rx_buffer = bytearray()

    def on_notify(_: int, data: bytearray) -> None:
        rx_buffer.extend(bytes(data))
        try:
            text = bytes(data).decode("utf-8", errors="replace")
            print(text, end="")
        except Exception:
            pass

    try:
        await client.connect()
        for _ in range(3):
            try:
                await client.start_notify(NOTIFY_UUID, on_notify)
                break
            except Exception:
                await asyncio.sleep(0.7)
        else:
            print("NOTIFY: failed")
            await client.disconnect()
            return False

        async def send(cmd: str) -> None:
            payload = (cmd.strip() + "\r\n").encode("utf-8")
            await client.write_gatt_char(WRITE_UUID, payload, response=False)

        async def wait_prompt(timeout_s: float = 2.5) -> bool:
            start = time.time()
            while time.time() - start < timeout_s:
                if PROMPT_TOKEN in rx_buffer:
                    return True
                await asyncio.sleep(0.05)
            return False

        rx_buffer.clear()
        await send("ping")
        await wait_prompt(2.5)

        for cmd in [
            "ping",
            "status",
            "compass raw",
            "compass",
            "compass stream on",
            "left pulse",
            "right pulse",
            "both pulse",
            "stop",
        ]:
            print(f"\n>> {cmd}")
            await send(cmd)
            await wait_prompt(3.0)

        await client.stop_notify(NOTIFY_UUID)
        await client.disconnect()
        print("\nTEST: ok")
        return True
    except Exception as exc:
        print(f"TEST: error {exc}")
        try:
            await client.disconnect()
        except Exception:
            pass
        return False


async def main() -> None:
    attempt = 0
    while True:
        attempt += 1
        print(f"\nATTEMPT {attempt}")
        ok = await run_full_test_once()
        if ok:
            return
        await asyncio.sleep(3.0)


if __name__ == "__main__":
    asyncio.run(main())
