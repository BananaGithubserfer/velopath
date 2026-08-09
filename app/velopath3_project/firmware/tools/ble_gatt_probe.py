import asyncio
from bleak import BleakClient

ADDRESS = "D4:AD:20:B1:B4:DA"


async def main() -> None:
    client = BleakClient(ADDRESS, timeout=12.0, winrt={"use_cached_services": False})
    try:
        print(f"CONNECT {ADDRESS}")
        await client.connect()
        print("CONNECTED")
        services = await client.get_services()
        print(f"SERVICES {len(services)}")
        for svc in services:
            print(f"SVC {svc.uuid}")
            for char in svc.characteristics:
                print(f"  CHAR {char.uuid} props={char.properties}")
    except Exception as exc:
        print(f"ERROR {exc}")
    finally:
        try:
            await client.disconnect()
        except Exception:
            pass


if __name__ == "__main__":
    asyncio.run(main())
