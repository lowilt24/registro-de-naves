import com.kms.katalon.core.testobject.RequestObject
import com.kms.katalon.core.testobject.ResponseObject
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import com.kms.katalon.core.testobject.TestObjectProperty
import com.kms.katalon.core.testobject.ConditionType
import com.kms.katalon.core.testobject.impl.HttpTextBodyContent
import groovy.json.JsonSlurper

/**
 * TC-08 — HU-05, positivo: una persona natural no necesita pais de
 * constitucion.
 *
 * Criterio de aceptacion: 201 y el campo paisConstitucion queda nulo,
 * aunque el cliente lo haya enviado. Una persona natural no se
 * constituye en ningun pais.
 */

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

String sufijo = System.currentTimeMillis()

String cuerpoNave = """{
  "nombre": "Nave HU05 Natural ${sufijo}",
  "tipo": "PESQUERA",
  "servicio": "CABOTAJE",
  "tonelajeBruto": 300.00,
  "tonelajeNeto": 180.00,
  "eslora": 28.00,
  "manga": 7.00,
  "puntal": 3.50,
  "anioConstruccion": 2016,
  "lugarConstruccion": "Astillero Vacamonte",
  "materialCasco": "Fibra",
  "tipoPropulsion": "Diesel",
  "potenciaKw": 450.00
}"""

RequestObject crearNave = new RequestObject('crearNave')
crearNave.setRestUrl(base + '/api/naves')
crearNave.setRestRequestMethod('POST')
crearNave.setHttpHeaderProperties([
    new TestObjectProperty('Content-Type', ConditionType.EQUALS, 'application/json'),
    new TestObjectProperty('Cookie', ConditionType.EQUALS, cookie)
])
crearNave.setBodyContent(new HttpTextBodyContent(cuerpoNave))

ResponseObject rNave = WS.sendRequest(crearNave)
WS.verifyResponseStatusCode(rNave, 201)
def naveId = new JsonSlurper().parseText(rNave.getResponseBodyContent()).id

// Se envia paisConstitucion a proposito: el backend debe descartarlo.
String cuerpoPropietario = """{
  "nombre": "Juan Perez ${sufijo}",
  "tipo": "NATURAL",
  "nacionalidad": "Panamena",
  "domicilio": "Via Espana, Ciudad de Panama",
  "paisConstitucion": "Panama",
  "identificacion": "8-888-${sufijo}",
  "naveId": ${naveId}
}"""

RequestObject crear = new RequestObject('registrarPropietario')
crear.setRestUrl(base + '/api/propietarios')
crear.setRestRequestMethod('POST')
crear.setHttpHeaderProperties([
    new TestObjectProperty('Content-Type', ConditionType.EQUALS, 'application/json'),
    new TestObjectProperty('Cookie', ConditionType.EQUALS, cookie)
])
crear.setBodyContent(new HttpTextBodyContent(cuerpoPropietario))

ResponseObject r = WS.sendRequest(crear)
println('Registro status: ' + r.getStatusCode())
println('Respuesta: ' + r.getResponseBodyContent())

WS.verifyResponseStatusCode(r, 201)

def creado = new JsonSlurper().parseText(r.getResponseBodyContent())
WS.verifyEqual(creado.tipo, 'NATURAL')
WS.verifyEqual(creado.paisConstitucion, null)
WS.verifyEqual(creado.naveId, naveId)
