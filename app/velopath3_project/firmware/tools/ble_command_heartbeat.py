"""Run a heartbeat by sending individual LED commands over BLE."""

import argparse
import asyncio

import ble_stable_terminal as terminal


COMMAND_SEQUENCE = ("left on", "left off", "right on", "right off")


async def run_heartbeat(cycles: int, interval_ms: int) -> None:
    client = None
    terminal.prepare_windows_ble()
    try:
        client, rx_buffer = await terminal.open_session()
        if not await terminal.probe_terminal(client, rx_buffer):
            raise RuntimeError("Probe command did not yield terminal prompt")

        print("COMMAND HEARTBEAT")
        delay_s = interval_ms / 1000.0
        for cycle in range(1, cycles + 1):
            print(f"\nCYCLE {cycle}/{cycles}")
            for command in COMMAND_SEQUENCE:
                print(f">> {command}")
                rx_buffer.clear()
                await client.send_line(command)
                if not await terminal.wait_for_prompt(rx_buffer, timeout_s=2.5):
                    raise RuntimeError(f"No response prompt for command: {command}")
                await asyncio.sleep(delay_s)

        rx_buffer.clear()
        await client.send_line("stop")
        await terminal.wait_for_prompt(rx_buffer, timeout_s=2.5)
        print("\nCOMMAND HEARTBEAT COMPLETE; stop sent")
    finally:
        if client is not None:
            await client.disconnect()


async def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cycles", type=int, default=10)
    parser.add_argument("--interval-ms", type=int, default=500)
    args = parser.parse_args()
    if args.cycles < 1:
        parser.error("--cycles must be at least 1")
    if args.interval_ms < 0:
        parser.error("--interval-ms must not be negative")

    await run_heartbeat(args.cycles, args.interval_ms)


if __name__ == "__main__":
    asyncio.run(main())
