import com.kms.katalon.core.testobject.RequestObject
import com.kms.katalon.core.testobject.ResponseObject
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import com.kms.katalon.core.testobject.TestObjectProperty
import com.kms.katalon.core.testobject.ConditionType
import com.kms.katalon.core.testobject.impl.HttpTextBodyContent
import groovy.json.JsonSlurper

/**
 * TC-09 — HU-05, negativo: una persona juridica sin pais de constitucion
 * se rechaza.
 *
 * Criterio de aceptacion: 400 y el error debe indicar el campo
 * concreto (paisConstitucion), no un mensaje generico.
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
  "nombre": "Nave HU05 Sin Pais ${sufijo}",
  "tipo": "CARGA",
  "servicio": "INTERNACIONAL",
  "tonelajeBruto": 2500.00,
  "tonelajeNeto": 1300.00,
  "eslora": 95.00,
  "manga": 15.00,
  "puntal": 8.00,
  "anioConstruccion": 2021,
  "lugarConstruccion": "Astillero Balboa",
  "materialCasco": "Acero",
  "tipoPropulsion": "Diesel",
  "potenciaKw": 3200.00
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

// Persona juridica sin paisConstitucion: debe fallar.
String cuerpoPropietario = """{
  "nombre": "Sociedad Incompleta ${sufijo} S.A.",
  "tipo": "JURIDICA",
  "nacionalidad": "Panamena",
  "domicilio": "Avenida Balboa, Ciudad de Panama",
  "identificacion": "RUC-SP-${sufijo}",
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

WS.verifyResponseStatusCode(r, 400)
// El error tiene que senalar el campo, no solo decir que algo fallo.
WS.verifyMatch(r.getResponseBodyContent(), '.*"paisConstitucion".*', true)

def error = new JsonSlurper().parseText(r.getResponseBodyContent())
WS.verifyNotEqual(error.campos.paisConstitucion, null)

// La nave no debe quedar con propietarios.
RequestObject listar = new RequestObject('listarPropietarios')
listar.setRestUrl(base + '/api/naves/' + naveId + '/propietarios')
listar.setRestRequestMethod('GET')
listar.setHttpHeaderProperties([
    new TestObjectProperty('Cookie', ConditionType.EQUALS, cookie)
])

ResponseObject rLista = WS.sendRequest(listar)
WS.verifyResponseStatusCode(rLista, 200)
WS.verifyEqual(new JsonSlurper().parseText(rLista.getResponseBodyContent()).size(), 0)
