import asyncio
from typing import Iterable

from bleak import BleakClient, BleakScanner


TARGET_NAME = "WH-BLE 106"
TEST_COMMANDS = [b"ping\n", b"help\n", b"compass\n"]


def format_uuid(uuid: str) -> str:
    return uuid.lower()


async def find_target() -> tuple[str, str]:
    devices = await BleakScanner.discover(timeout=6.0, return_adv=True)
    for _, pair in devices.items():
        device, adv = pair
        name = adv.local_name or device.name or ""
        if name == TARGET_NAME:
            return device.address, name
    raise RuntimeError(f"BLE device '{TARGET_NAME}' not found")


async def try_read_all(client: BleakClient, uuids: Iterable[str]) -> None:
    for uuid in uuids:
        try:
            data = await client.read_gatt_char(uuid)
            print(f"READ {uuid}: {data!r}")
        except Exception as exc:  # noqa: BLE001
            print(f"READ {uuid} failed: {exc}")


async def main() -> None:
    address, name = await find_target()
    print(f"FOUND {name} {address}")

    notifications: list[tuple[str, bytes]] = []

    def notification_handler(uuid: str):
        def _handler(_: object, data: bytearray) -> None:
            payload = bytes(data)
            notifications.append((uuid, payload))
            print(f"NOTIFY {uuid}: {payload!r}")

        return _handler

    async with BleakClient(address, timeout=12.0) as client:
        print(f"CONNECTED {client.is_connected}")
        services = client.services
        write_uuids: list[str] = []
        read_uuids: list[str] = []

        for service in services:
            print(f"SERVICE {service.uuid}")
            for char in service.characteristics:
                props = ",".join(char.properties)
                print(f"  CHAR {char.uuid} [{props}]")
                if "notify" in char.properties or "indicate" in char.properties:
                    try:
                        await client.start_notify(char.uuid, notification_handler(char.uuid))
                        print(f"  NOTIFY-ON {char.uuid}")
                    except Exception as exc:  # noqa: BLE001
                        print(f"  NOTIFY-FAIL {char.uuid}: {exc}")
                if "write" in char.properties or "write-without-response" in char.properties:
                    write_uuids.append(char.uuid)
                if "read" in char.properties:
                    read_uuids.append(char.uuid)

        await try_read_all(client, read_uuids)

        for uuid in write_uuids:
            for command in TEST_COMMANDS:
                for response_mode in (True, False):
                    try:
                        await client.write_gatt_char(uuid, command, response=response_mode)
                        print(
                            f"WRITE {uuid} response={response_mode}: {command!r}"
                        )
                    except Exception as exc:  # noqa: BLE001
                        print(
                            f"WRITE-FAIL {uuid} response={response_mode}: {command!r} -> {exc}"
                        )
                    await asyncio.sleep(1.0)
                    await try_read_all(client, read_uuids)

        await asyncio.sleep(2.0)
        print(f"NOTIFICATION COUNT {len(notifications)}")


if __name__ == "__main__":
    asyncio.run(main())
