Add-Type -AssemblyName System.Runtime.WindowsRuntime

function AwaitWinRt($operation, $resultType) {
    $m = [System.WindowsRuntimeSystemExtensions].GetMethods() |
        Where-Object { $_.Name -eq 'AsTask' -and $_.IsGenericMethod -and $_.GetParameters().Count -eq 1 } |
        Select-Object -First 1
    $gm = $m.MakeGenericMethod($resultType)
    $task = $gm.Invoke($null, @($operation))
    $task.GetAwaiter().GetResult()
}

$selector = [Windows.Devices.Bluetooth.BluetoothLEDevice,Windows.Devices.Bluetooth,ContentType=WindowsRuntime]::GetDeviceSelector()
$op = [Windows.Devices.Enumeration.DeviceInformation,Windows.Devices.Enumeration,ContentType=WindowsRuntime]::FindAllAsync($selector)
$devices = AwaitWinRt $op ([Windows.Devices.Enumeration.DeviceInformationCollection])
$device = $devices | Where-Object { $_.Name -eq 'WH-BLE 106' } | Select-Object -First 1

if (-not $device) {
    Write-Output "DEVICE NOT FOUND"
    exit 0
}

Write-Output ("PAIRED BEFORE {0}" -f $device.Pairing.IsPaired)

if (-not $device.Pairing.IsPaired) {
    $pairResult = AwaitWinRt ($device.Pairing.PairAsync()) ([Windows.Devices.Enumeration.DevicePairingResult])
    Write-Output ("PAIR STATUS {0}" -f $pairResult.Status)
}
