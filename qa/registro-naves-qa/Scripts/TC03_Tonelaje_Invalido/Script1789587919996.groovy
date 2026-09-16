import com.kms.katalon.core.testobject.RequestObject
import com.kms.katalon.core.testobject.ResponseObject
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import com.kms.katalon.core.testobject.TestObjectProperty
import com.kms.katalon.core.testobject.ConditionType
import internal.GlobalVariable

String base = 'http://localhost:8080'

// --- 1. Login: obtener cookie de sesion ---
RequestObject login = new RequestObject('login')
login.setRestUrl(base + '/api/auth/login')
login.setRestRequestMethod('POST')
login.setHttpHeaderProperties([
    new TestObjectProperty('Content-Type', ConditionType.EQUALS,
        'application/x-www-form-urlencoded')
])
login.setBodyContent(new com.kms.katalon.core.testobject.impl.HttpTextBodyContent(
    'correo=agente@navesitas.pa&password=Navesitas2026*'))

ResponseObject rLogin = WS.sendRequest(login)
println('Login status: ' + rLogin.getStatusCode())

String cookie = rLogin.getHeaderFields()['Set-Cookie']?.get(0)?.split(';')?.getAt(0)
println('Cookie: ' + cookie)

// --- 2. Registrar nave valida ---
String cuerpo = '''{
  "nombre": "Nave Prueba Katalon",
  "tipo": "CARGA",
  "servicio": "CABOTAJE",
  "tonelajeBruto": 0,
  "tonelajeNeto": 900.25,
  "eslora": 85.30,
  "manga": 14.20,
  "puntal": 7.50,
  "anioConstruccion": 2018,
  "lugarConstruccion": "Astillero Balboa",
  "materialCasco": "Acero",
  "tipoPropulsion": "Diesel",
  "potenciaKw": 2400.00,
  "propietarioId": 1,
  "agenteResidenteId": 1
}'''

RequestObject crear = new RequestObject('crearNave')
crear.setRestUrl(base + '/api/naves')
crear.setRestRequestMethod('POST')
crear.setHttpHeaderProperties([
	new TestObjectProperty('Content-Type', ConditionType.EQUALS, 'application/json'),
	new TestObjectProperty('Cookie', ConditionType.EQUALS, cookie)
])
crear.setBodyContent(new com.kms.katalon.core.testobject.impl.HttpTextBodyContent(cuerpo))

ResponseObject rCrear = WS.sendRequest(crear)
println('Registro status: ' + rCrear.getStatusCode())
println('Respuesta: ' + rCrear.getResponseBodyContent())

WS.verifyResponseStatusCode(rCrear, 400)
WS.verifyMatch(rCrear.getResponseBodyContent(),
	'.*El tonelaje bruto debe ser mayor que cero.*', true)