import com.kms.katalon.core.testobject.RequestObject
import com.kms.katalon.core.testobject.ResponseObject
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import com.kms.katalon.core.testobject.TestObjectProperty
import com.kms.katalon.core.testobject.ConditionType
import com.kms.katalon.core.testobject.impl.HttpTextBodyContent

String base = 'http://localhost:8080'

RequestObject login = new RequestObject('login')
login.setRestUrl(base + '/api/auth/login')
login.setRestRequestMethod('POST')
login.setHttpHeaderProperties([
    new TestObjectProperty('Content-Type', ConditionType.EQUALS,
        'application/x-www-form-urlencoded')
])
login.setBodyContent(new HttpTextBodyContent(
    'correo=agente@navesitas.pa&password=Navesitas2026*'))

ResponseObject rLogin = WS.sendRequest(login)
String cookie = rLogin.getHeaderFields()['Set-Cookie']?.get(0)?.split(';')?.getAt(0)

String nombre = 'Nave Duplicada ' + System.currentTimeMillis()

String cuerpo = """{
  "nombre": "${nombre}",
  "tipo": "CARGA",
  "servicio": "CABOTAJE",
  "tonelajeBruto": 1200.00,
  "tonelajeNeto": 800.00,
  "eslora": 70.00,
  "manga": 12.00,
  "puntal": 6.00,
  "anioConstruccion": 2020,
  "lugarConstruccion": "Astillero Balboa",
  "materialCasco": "Acero",
  "tipoPropulsion": "Diesel",
  "potenciaKw": 1800.00,
  "propietarioId": 1,
  "agenteResidenteId": 1
}"""

RequestObject crear = new RequestObject('crearNave')
crear.setRestUrl(base + '/api/naves')
crear.setRestRequestMethod('POST')
crear.setHttpHeaderProperties([
	new TestObjectProperty('Content-Type', ConditionType.EQUALS, 'application/json'),
	new TestObjectProperty('Cookie', ConditionType.EQUALS, cookie)
])
crear.setBodyContent(new HttpTextBodyContent(cuerpo))

// Primer registro: debe crearse
ResponseObject r1 = WS.sendRequest(crear)
println('Primer registro: ' + r1.getStatusCode())
WS.verifyResponseStatusCode(r1, 201)

// Segundo registro con el mismo nombre: debe rechazarse
ResponseObject r2 = WS.sendRequest(crear)
println('Segundo registro: ' + r2.getStatusCode())
println('Respuesta: ' + r2.getResponseBodyContent())
WS.verifyResponseStatusCode(r2, 409)