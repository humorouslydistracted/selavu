# Download gradle-wrapper.jar for Gradle 8.5
$url = "https://raw.githubusercontent.com/gradle/gradle/v8.5.0/gradle/wrapper/gradle-wrapper.jar"
$output = "gradle\wrapper\gradle-wrapper.jar"
Invoke-WebRequest -Uri $url -OutFile $output
Write-Host "Downloaded gradle-wrapper.jar"