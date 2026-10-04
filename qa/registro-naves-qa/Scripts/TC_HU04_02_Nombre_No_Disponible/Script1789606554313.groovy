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

String nombre = 'Nave Ocupada ' + System.currentTimeMillis()

String cuerpo = """{
  "nombre": "${nombre}",
  "tipo": "CARGA",
  "servicio": "CABOTAJE",
  "tonelajeBruto": 1000.00,
  "tonelajeNeto": 700.00,
  "eslora": 60.00,
  "manga": 11.00,
  "puntal": 5.00,
  "anioConstruccion": 2019,
  "lugarConstruccion": "Astillero Balboa",
  "materialCasco": "Acero",
  "tipoPropulsion": "Diesel",
  "potenciaKw": 1500.00,
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

ResponseObject rCrear = WS.sendRequest(crear)
WS.verifyResponseStatusCode(rCrear, 201)

// Ahora el nombre ya esta tomado.
// La consulta tambien exige sesion desde el Sprint 3 (hallazgo SEC-04):
// sin la cookie responde 401 y no llega a comparar el nombre.
RequestObject consulta = new RequestObject('disponibilidad')
consulta.setRestUrl(base + '/api/naves/disponibilidad?nombre=' +
    java.net.URLEncoder.encode(nombre, 'UTF-8'))
consulta.setRestRequestMethod('GET')
consulta.setHttpHeaderProperties([
    new TestObjectProperty('Cookie', ConditionType.EQUALS, cookie)
])

ResponseObject r = WS.sendRequest(consulta)
println('Respuesta: ' + r.getResponseBodyContent())

WS.verifyResponseStatusCode(r, 200)
WS.verifyMatch(r.getResponseBodyContent(), '.*"disponible":false.*', true)