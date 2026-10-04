import com.kms.katalon.core.testobject.RequestObject
import com.kms.katalon.core.testobject.ResponseObject
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import com.kms.katalon.core.testobject.TestObjectProperty
import com.kms.katalon.core.testobject.ConditionType
import com.kms.katalon.core.testobject.impl.HttpTextBodyContent
import groovy.json.JsonSlurper

/**
 * TC-07 — HU-05, positivo: registrar una persona juridica y vincularla
 * a una nave propia.
 *
 * Criterio de aceptacion: 201, el propietario queda con tipo JURIDICA y
 * con el 100% de participacion cuando no se envia el porcentaje, y
 * aparece en el listado de propietarios de la nave.
 */

String base = 'http://localhost:8080'

// --- 1. Iniciar sesion. Desde el Sprint 3 todo /api/** la exige. ---
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

// --- 2. Registrar una nave propia. HU-05 solo permite vincular
//        propietarios a naves registradas por el mismo usuario. ---
String cuerpoNave = """{
  "nombre": "Nave HU05 ${sufijo}",
  "tipo": "CARGA",
  "servicio": "CABOTAJE",
  "tonelajeBruto": 1200.00,
  "tonelajeNeto": 800.00,
  "eslora": 70.00,
  "manga": 12.00,
  "puntal": 6.00,
  "anioConstruccion": 2019,
  "lugarConstruccion": "Astillero Balboa",
  "materialCasco": "Acero",
  "tipoPropulsion": "Diesel",
  "potenciaKw": 1800.00
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
println('Nave creada con id: ' + naveId)

// --- 3. Registrar el propietario juridico y vincularlo. ---
String cuerpoPropietario = """{
  "nombre": "Naviera Ejemplo ${sufijo} S.A.",
  "tipo": "JURIDICA",
  "nacionalidad": "Panamena",
  "domicilio": "Calle 50, Ciudad de Panama",
  "paisConstitucion": "Panama",
  "identificacion": "RUC-${sufijo}",
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
WS.verifyEqual(creado.tipo, 'JURIDICA')
WS.verifyEqual(creado.paisConstitucion, 'Panama')
WS.verifyEqual(creado.naveId, naveId)
// Sin porcentaje explicito el backend asume el 100%.
WS.verifyEqual(creado.porcentaje as BigDecimal, 100.00 as BigDecimal)

// --- 4. El propietario aparece en el expediente de la nave. ---
RequestObject listar = new RequestObject('listarPropietarios')
listar.setRestUrl(base + '/api/naves/' + naveId + '/propietarios')
listar.setRestRequestMethod('GET')
listar.setHttpHeaderProperties([
    new TestObjectProperty('Cookie', ConditionType.EQUALS, cookie)
])

ResponseObject rLista = WS.sendRequest(listar)
println('Listado: ' + rLista.getResponseBodyContent())

WS.verifyResponseStatusCode(rLista, 200)
def lista = new JsonSlurper().parseText(rLista.getResponseBodyContent())
WS.verifyEqual(lista.size(), 1)
WS.verifyEqual(lista[0].nombre, 'Naviera Ejemplo ' + sufijo + ' S.A.')
