import asyncio

from bleak import BleakClient, BleakScanner
from bleak.backends.device import BLEDevice
from bleak.backends.winrt.util import uninitialize_sta


TARGET_NAME = "WH-BLE 106"
SERVICE_UUID = "0003cdd0-0000-1000-8000-00805f9b0131"
NOTIFY_UUID = "0003cdd1-0000-1000-8000-00805f9b0131"
WRITE_UUID = "0003cdd2-0000-1000-8000-00805f9b0131"


def prepare_windows_ble() -> None:
    uninitialize_sta()


async def find_target() -> BLEDevice:
    devices = await BleakScanner.discover(timeout=6.0, return_adv=True)
    for _, pair in devices.items():
        device, adv = pair
        name = adv.local_name or device.name or ""
        if name == TARGET_NAME:
            return device
    raise RuntimeError(f"BLE device '{TARGET_NAME}' not found")


async def main() -> None:
    prepare_windows_ble()
    device = await find_target()
    print(f"FOUND {TARGET_NAME} {device.address}")

    def on_notify(_: object, data: bytearray) -> None:
        print(f"NOTIFY {bytes(data)!r}")

    candidates = (
        {"target": device, "pair": False, "winrt": {"use_cached_services": True}},
        {"target": device, "pair": False, "winrt": {"use_cached_services": False}},
        {"target": device.address, "pair": False, "winrt": {"use_cached_services": True}},
        {"target": device.address, "pair": True, "winrt": {"use_cached_services": True}},
    )
    client = None
    service = None
    last_error: Exception | None = None
    notify_started = False

    for candidate in candidates:
        try:
            client = BleakClient(
                candidate["target"],
                timeout=25.0,
                pair=candidate["pair"],
                winrt=candidate["winrt"],
            )
            await client.connect()
            print(
                "CONNECTED {0} pair={1} cached={2} target={3}".format(
                    client.is_connected,
                    candidate["pair"],
                    candidate["winrt"]["use_cached_services"],
                    "BLEDevice" if isinstance(candidate["target"], BLEDevice) else "address",
                )
            )
            await asyncio.sleep(0.8)
            service = client.services.get_service(SERVICE_UUID)
            if service is not None:
                try:
                    await client.start_notify(NOTIFY_UUID, on_notify)
                    print("NOTIFY-ON")
                    notify_started = True
                    break
                except Exception as exc:  # noqa: BLE001
                    last_error = exc
            await client.disconnect()
            client = None
            if service is None:
                last_error = RuntimeError("Target service not found")
        except Exception as exc:  # noqa: BLE001
            last_error = exc
            if client is not None and client.is_connected:
                try:
                    await client.disconnect()
                except Exception:  # noqa: BLE001
                    pass
            client = None
            await asyncio.sleep(0.8)

    if client is None or service is None or not notify_started:
        raise RuntimeError(f"Unable to open BLE session: {last_error}")

    try:
        services = client.services

        for char in service.characteristics:
            print(f"CHAR {char.uuid} {char.properties}")

        try:
            await client.write_gatt_char(WRITE_UUID, b"000000", response=False)
            print("PASSWORD SENT 000000")
            await asyncio.sleep(0.5)
        except Exception as exc:  # noqa: BLE001
            print(f"PASSWORD-FAIL {exc}")

        for payload in (b"ping\n", b"help\n", b"compass\n"):
            for response in (False, True):
                try:
                    await client.write_gatt_char(WRITE_UUID, payload, response=response)
                    print(f"WRITE OK response={response} payload={payload!r}")
                except Exception as exc:  # noqa: BLE001
                    print(f"WRITE FAIL response={response} payload={payload!r} err={exc}")
                await asyncio.sleep(1.0)

        await asyncio.sleep(3.0)
    finally:
        if client is not None and client.is_connected:
            await client.disconnect()


if __name__ == "__main__":
    asyncio.run(main())
