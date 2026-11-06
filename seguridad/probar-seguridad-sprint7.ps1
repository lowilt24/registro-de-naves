param(
    [string]$Base = 'http://localhost:8080',
    [string]$Salida = 'seguridad/reportes/s7-seguridad.json'
)
# HU-13 / Sprint 7. Lecturas y POST invalidos: no crea ni modifica datos.
# Usa cuentas demo y cookies CSRF reales. No lee .env ni guarda cookies en reportes.
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
$claveDemo = if ($env:NAVESITAS_CLAVE) { $env:NAVESITAS_CLAVE } else { 'Navesitas2026*' }
$resultados = [Collections.Generic.List[object]]::new()
$clientes = [Collections.Generic.List[object]]::new()
function Nueva-Sesion {
    $jar = [Net.CookieContainer]::new()
    $handler = [Net.Http.HttpClientHandler]::new()
    $handler.CookieContainer = $jar
    $handler.AllowAutoRedirect = $false
    $client = [Net.Http.HttpClient]::new($handler)
    $client.Timeout = [TimeSpan]::FromSeconds(15)
    $clientes.Add($client)
    return @{ Cliente=$client; Cookies=$jar }
}
function Pedir($sesion, [string]$ruta, [string]$metodo = 'GET', [string]$cuerpo = $null, [string]$token = $null, [string]$tipo = 'application/json') {
    $request = [Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::new($metodo), $Base.TrimEnd('/') + $ruta)
    try {
        if ($metodo -ne 'GET' -and $null -ne $cuerpo) { $request.Content = [Net.Http.StringContent]::new($cuerpo, [Text.Encoding]::UTF8, $tipo) }
        if ($token) { $request.Headers.Add('X-XSRF-TOKEN', $token) }
        $response = $sesion.Cliente.SendAsync($request).GetAwaiter().GetResult()
        try { return @{ Estado=[int]$response.StatusCode; Cuerpo=$response.Content.ReadAsStringAsync().GetAwaiter().GetResult() } }
        finally { $response.Dispose() }
    } finally { $request.Dispose() }
}
function Login([string]$correo) {
    $sesion = Nueva-Sesion
    $null = Pedir $sesion '/login.html'
    $token = $sesion.Cookies.GetCookies([Uri]$Base)['XSRF-TOKEN'].Value
    $form = 'correo=' + [Uri]::EscapeDataString($correo) + '&password=' + [Uri]::EscapeDataString($claveDemo)
    $login = Pedir $sesion '/api/auth/login' 'POST' $form $token 'application/x-www-form-urlencoded'
    if ($login.Estado -ne 302) { throw 'No se pudo iniciar sesion demo.' }
    $estado = Pedir $sesion '/api/auth/sesion'
    if (-not ($estado.Cuerpo | ConvertFrom-Json | ForEach-Object { $_ }).autenticado) { throw 'Cuenta demo no autenticada.' }
    return $sesion
}
function Comprobar([string]$caso, $respuesta, [int]$esperado) {
    $correcto = $respuesta.Estado -eq $esperado
    $resultados.Add([pscustomobject]@{ caso=$caso; esperado=$esperado; obtenido=$respuesta.Estado; correcto=$correcto })
    Write-Output ('{0}: HTTP {1}, esperado {2}' -f $caso,$respuesta.Estado,$esperado)
}
try {
    $anonimo = Nueva-Sesion
    $agente = Login 'agente@navesitas.pa'
    $dgmm = Login 'revisor@navesitas.pa'
    $auditor = Login 'auditor@navesitas.pa'
    $admin = Login 'admin@navesitas.pa'
    $propias = @((Pedir $agente '/api/naves').Cuerpo | ConvertFrom-Json | ForEach-Object { $_ })
    if ($propias.Count -eq 0) { throw 'Hace falta una nave del agente demo; esta comprobacion no crea datos.' }
    $naveId = $propias[0].id
    $ruta = '/api/naves/' + $naveId + '/documentos/requisitos'
    Comprobar 'Anonimo sin acceso' (Pedir $anonimo $ruta) 401
    Comprobar 'Agente propietario lee' (Pedir $agente $ruta) 200
    Comprobar 'DGMM lee' (Pedir $dgmm $ruta) 200
    Comprobar 'Auditor lee' (Pedir $auditor $ruta) 200
    Comprobar 'Administrador sin acceso al expediente ajeno' (Pedir $admin $ruta) 403
    foreach ($dato in @(@('abc',400),@('0',404),@('-1',404),@('999999999',404),@("' OR '1'='1",400))) {
        $idFuzz = [Uri]::EscapeDataString([string]$dato[0])
        Comprobar ('ID invalido: '+$dato[0]) (Pedir $agente ('/api/naves/'+$idFuzz+'/documentos/requisitos')) $dato[1]
    }
    $bandeja = @((Pedir $dgmm '/api/tramites').Cuerpo | ConvertFrom-Json | ForEach-Object { $_ })
    $idsPropias = @($propias | ForEach-Object { $_.id })
    $ajena = $bandeja | Where-Object { $_.naveId -notin $idsPropias } | Select-Object -First 1
    if ($ajena) { Comprobar 'IDOR: agente no lee nave ajena' (Pedir $agente ('/api/naves/'+$ajena.naveId+'/documentos/requisitos')) 403 }
    else { $resultados.Add([pscustomobject]@{caso='IDOR agente'; estado='PENDIENTE'; motivo='No hay nave ajena disponible en la bandeja'}) }
    $cambio = '/api/naves/'+$naveId+'/tramite/estado'
    Comprobar 'CSRF ausente bloquea POST' (Pedir $agente $cambio 'POST' '{}') 403
    Comprobar 'CSRF incorrecto bloquea POST' (Pedir $agente $cambio 'POST' '{}' 'incorrecto') 403
    $token = $agente.Cookies.GetCookies([Uri]$Base)['XSRF-TOKEN'].Value
    Comprobar 'CSRF real llega a validacion 400' (Pedir $agente $cambio 'POST' '{}' $token) 400
} finally {
    foreach($client in $clientes) { $client.Dispose() }
    $directorio = Split-Path -Parent $Salida
    if ($directorio) { New-Item -ItemType Directory -Force -Path $directorio | Out-Null }
    $resultados | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $Salida -Encoding utf8
}
if (@($resultados | Where-Object { $_.correcto -eq $false }).Count -gt 0) { throw 'Hay comprobaciones de seguridad fallidas; revise el reporte.' }
