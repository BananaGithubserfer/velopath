import asyncio
from collections import defaultdict

from bleak import BleakClient, BleakScanner


TARGET_NAME = "WH-BLE 106"
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
    markers: dict[str, int] = {}

    def on_notify(_: object, data: bytearray) -> None:
        text = bytes(data).decode("utf-8", errors="replace")
        notifications.append(text)
        print(f"NOTIFY {text!r}")

    async with BleakClient(address, timeout=15.0, pair=True) as client:
        print(f"CONNECTED {client.is_connected}")

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
            markers[command] = len(notifications)
            payload = (command + "\n").encode("utf-8")
            await client.write_gatt_char(WRITE_UUID, payload, response=False)
            print(f"SENT {command!r}")
            await asyncio.sleep(1.2)

        await asyncio.sleep(1.5)
        await client.stop_notify(NOTIFY_UUID)

    print("\nRESULTS")
    for index, command in enumerate(COMMANDS):
        start = markers[command]
        end = len(notifications)
        if index + 1 < len(COMMANDS):
            end = markers[COMMANDS[index + 1]]
        chunks = notifications[start:end]
        combined = "".join(chunks).replace("\r", "\\r").replace("\n", "\\n")
        print(f"{command}: {combined}")


if __name__ == "__main__":
    asyncio.run(main())
