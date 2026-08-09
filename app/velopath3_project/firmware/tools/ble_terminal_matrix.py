import asyncio

from bleak import BleakClient, BleakScanner


TARGET_NAME = "WH-BLE 106"
SERVICE_UUID = "0003cdd0-0000-1000-8000-00805f9b0131"
NOTIFY_UUID = "0003cdd1-0000-1000-8000-00805f9b0131"
WRITE_UUID = "0003cdd2-0000-1000-8000-00805f9b0131"

COMMANDS = [
    "help",
    "ping",
    "compass",
    "left on",
    "left off",
    "right on",
    "right off",
    "both on",
    "both off",
    "stop",
    "unknown",
]


async def find_target() -> str:
    devices = await BleakScanner.discover(timeout=6.0, return_adv=True)
    for _, pair in devices.items():
        device, adv = pair
        name = adv.local_name or device.name or ""
        if name == TARGET_NAME:
            return device.address
    raise RuntimeError(f"BLE device '{TARGET_NAME}' not found")


async def main() -> None:
    address = await find_target()
    print(f"FOUND {TARGET_NAME} {address}")
    notifications: list[str] = []
    boundaries: list[tuple[str, int]] = []

    def on_notify(_: object, data: bytearray) -> None:
        text = bytes(data).decode("utf-8", errors="replace")
        notifications.append(text)
        print(f"NOTIFY {text!r}")

    async with BleakClient(address, timeout=15.0, pair=True) as client:
        print(f"CONNECTED {client.is_connected}")

        service = client.services.get_service(SERVICE_UUID)
        if service is None:
            raise RuntimeError("Target service not found")

        for char in service.characteristics:
            print(f"CHAR {char.uuid} {char.properties}")

        try:
            await client.write_gatt_char(WRITE_UUID, b"000000", response=False)
            print("PASSWORD SENT 000000")
            await asyncio.sleep(0.5)
        except Exception as exc:  # noqa: BLE001
            print(f"PASSWORD-FAIL {exc}")

        await client.start_notify(NOTIFY_UUID, on_notify)
        print("NOTIFY-ON")
        await asyncio.sleep(0.5)

        for command in COMMANDS:
            payload = (command + "\n").encode("utf-8")
            for response in (False, True):
                label = f"{command} [response={response}]"
                boundaries.append((label, len(notifications)))
                try:
                    await client.write_gatt_char(WRITE_UUID, payload, response=response)
                    print(f"WRITE OK {label}")
                except Exception as exc:  # noqa: BLE001
                    print(f"WRITE FAIL {label}: {exc}")
                await asyncio.sleep(1.2)

        await asyncio.sleep(2.0)

    print("\nRESULTS")
    for index, (label, start) in enumerate(boundaries):
        end = len(notifications)
        if index + 1 < len(boundaries):
            end = boundaries[index + 1][1]
        combined = "".join(notifications[start:end]).replace("\r", "\\r").replace("\n", "\\n")
        print(f"{label}: {combined}")


if __name__ == "__main__":
    asyncio.run(main())
