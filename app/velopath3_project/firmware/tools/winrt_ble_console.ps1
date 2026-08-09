Add-Type -AssemblyName System.Runtime.WindowsRuntime

function AwaitWinRt($operation, $resultType) {
    $m = [System.WindowsRuntimeSystemExtensions].GetMethods() |
        Where-Object { $_.Name -eq 'AsTask' -and $_.IsGenericMethod -and $_.GetParameters().Count -eq 1 } |
        Select-Object -First 1
    $gm = $m.MakeGenericMethod($resultType)
    $task = $gm.Invoke($null, @($operation))
    $task.GetAwaiter().GetResult()
}

function New-Buffer([byte[]]$Bytes) {
    $writer = [Windows.Storage.Streams.DataWriter, Windows.Storage.Streams, ContentType=WindowsRuntime]::new()
    $writer.WriteBytes($Bytes)
    $writer.DetachBuffer()
}

$target = 'WH-BLE 106'
$serviceUuid = [guid]'0003cdd0-0000-1000-8000-00805f9b0131'
$notifyUuid = [guid]'0003cdd1-0000-1000-8000-00805f9b0131'
$writeUuid = [guid]'0003cdd2-0000-1000-8000-00805f9b0131'

$selector = [Windows.Devices.Bluetooth.BluetoothLEDevice,Windows.Devices.Bluetooth,ContentType=WindowsRuntime]::GetDeviceSelector()
$devicesOp = [Windows.Devices.Enumeration.DeviceInformation,Windows.Devices.Enumeration,ContentType=WindowsRuntime]::FindAllAsync($selector)
$devices = AwaitWinRt $devicesOp ([Windows.Devices.Enumeration.DeviceInformationCollection])
$deviceInfo = $devices | Where-Object { $_.Name -eq $target } | Select-Object -First 1

if (-not $deviceInfo) {
    throw "BLE device '$target' not found"
}

Write-Output ("FOUND {0} {1}" -f $deviceInfo.Name, $deviceInfo.Id)

$dev = AwaitWinRt ([Windows.Devices.Bluetooth.BluetoothLEDevice,Windows.Devices.Bluetooth,ContentType=WindowsRuntime]::FromIdAsync($deviceInfo.Id)) ([Windows.Devices.Bluetooth.BluetoothLEDevice])
$serviceResult = AwaitWinRt ($dev.GetGattServicesAsync([Windows.Devices.Bluetooth.BluetoothCacheMode]::Cached)) ([Windows.Devices.Bluetooth.GenericAttributeProfile.GattDeviceServicesResult])
Write-Output ("SERVICES STATUS {0}" -f $serviceResult.Status)

$service = $serviceResult.Services | Where-Object { $_.Uuid -eq $serviceUuid } | Select-Object -First 1
if (-not $service) {
    throw "Target service not found"
}

$charResult = AwaitWinRt ($service.GetCharacteristicsAsync([Windows.Devices.Bluetooth.BluetoothCacheMode]::Cached)) ([Windows.Devices.Bluetooth.GenericAttributeProfile.GattCharacteristicsResult])
Write-Output ("CHARS STATUS {0}" -f $charResult.Status)

$notifyChar = $charResult.Characteristics | Where-Object { $_.Uuid -eq $notifyUuid } | Select-Object -First 1
$writeChar = $charResult.Characteristics | Where-Object { $_.Uuid -eq $writeUuid } | Select-Object -First 1

Write-Output ("NOTIFY CHAR {0}" -f $notifyChar.AttributeHandle)
Write-Output ("WRITE CHAR {0}" -f $writeChar.AttributeHandle)

$passwordBuffer = New-Buffer ([System.Text.Encoding]::ASCII.GetBytes("000000"))
$passwordResult = AwaitWinRt (
    $writeChar.WriteValueAsync(
        $passwordBuffer,
        [Windows.Devices.Bluetooth.GenericAttributeProfile.GattWriteOption]::WriteWithoutResponse
    )
) ([Windows.Devices.Bluetooth.GenericAttributeProfile.GattCommunicationStatus])
Write-Output ("PASSWORD STATUS {0}" -f $passwordResult)

Start-Sleep -Milliseconds 500

$notifyStatus = AwaitWinRt (
    $notifyChar.WriteClientCharacteristicConfigurationDescriptorAsync(
        [Windows.Devices.Bluetooth.GenericAttributeProfile.GattClientCharacteristicConfigurationDescriptorValue]::Notify
    )
) ([Windows.Devices.Bluetooth.GenericAttributeProfile.GattCommunicationStatus])
Write-Output ("NOTIFY STATUS {0}" -f $notifyStatus)

foreach ($cmd in @("ping`n", "help`n", "compass`n")) {
    $buffer = New-Buffer ([System.Text.Encoding]::ASCII.GetBytes($cmd))
    $result = AwaitWinRt (
        $writeChar.WriteValueAsync(
            $buffer,
            [Windows.Devices.Bluetooth.GenericAttributeProfile.GattWriteOption]::WriteWithoutResponse
        )
    ) ([Windows.Devices.Bluetooth.GenericAttributeProfile.GattCommunicationStatus])
    Write-Output ("WRITE {0} => {1}" -f $cmd.Trim(), $result)
    Start-Sleep -Seconds 1
}
