import com.kms.katalon.core.testobject.RequestObject
import com.kms.katalon.core.testobject.ResponseObject
import com.kms.katalon.core.testobject.TestObjectProperty
import com.kms.katalon.core.testobject.ConditionType
import com.kms.katalon.core.testobject.FormDataBodyParameter
import com.kms.katalon.core.testobject.impl.HttpTextBodyContent
import com.kms.katalon.core.testobject.impl.HttpFormDataBodyContent
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS
import navesitas.Http
import com.kms.katalon.core.webui.keyword.WebUiBuiltInKeywords as WebUI
import com.kms.katalon.core.model.FailureHandling
import static com.kms.katalon.core.testcase.TestCaseFactory.findTestCase
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

String base = 'http://localhost:8080'

/** Inicia sesion y devuelve la cookie JSESSIONID. */
def iniciarSesion = { String correo ->
    RequestObject login = new RequestObject('login')
    login.setRestUrl(base + '/api/auth/login')
    login.setRestRequestMethod('POST')
    login.setHttpHeaderProperties([
        new TestObjectProperty('Content-Type', ConditionType.EQUALS,
            'application/x-www-form-urlencoded')
    ])
    login.setBodyContent(new HttpTextBodyContent(
        'correo=' + correo + '&password=Navesitas2026*'))
    ResponseObject r = Http.enviar(login)
    return Http.sesion(r)
}

/** GET con la cookie de sesion (o sin ella si es null). */
def consultar = { String cookie, String ruta ->
    RequestObject get = new RequestObject('consultar')
    get.setRestUrl(base + ruta)
    get.setRestRequestMethod('GET')
    List<TestObjectProperty> cabeceras = []
    if (cookie) cabeceras << new TestObjectProperty('Cookie', ConditionType.EQUALS, cookie)
    get.setHttpHeaderProperties(cabeceras)
    return Http.enviar(get)
}

/** POST con cuerpo JSON y la cookie de sesion (o sin ella si es null). */
def postJson = { String cookie, String ruta, String cuerpo ->
    RequestObject p = new RequestObject('post')
    p.setRestUrl(base + ruta)
    p.setRestRequestMethod('POST')
    List<TestObjectProperty> cabeceras = [
        new TestObjectProperty('Content-Type', ConditionType.EQUALS, 'application/json')
    ]
    if (cookie) cabeceras << new TestObjectProperty('Cookie', ConditionType.EQUALS, cookie)
    p.setHttpHeaderProperties(cabeceras)
    p.setBodyContent(new HttpTextBodyContent(cuerpo))
    return Http.enviar(p)
}

def json = { ResponseObject r -> new JsonSlurper().parseText(r.getResponseBodyContent()) }

/** HU-09: POST /api/naves/{id}/tramite/estado con la accion y, si hay, la observacion. */
def accion = { String cookie, def naveId, String nombre, String observacion = null ->
    Map cuerpo = [accion: nombre]
    if (observacion != null) cuerpo.observacion = observacion
    ResponseObject r = postJson(cookie, '/api/naves/' + naveId + '/tramite/estado', JsonOutput.toJson(cuerpo))
    println(nombre + ' -> ' + r.getStatusCode() + ' ' + r.getResponseBodyContent())
    return r
}

/** HU-03: registra una nave con nombre unico y devuelve la respuesta. */
def registrarNave = { String cookie, String prefijo ->
    String nombre = prefijo + ' ' + System.currentTimeMillis()
    return postJson(cookie, '/api/naves', """{
  "nombre": "${nombre}",
  "tipo": "CARGA",
  "servicio": "INTERNACIONAL",
  "tonelajeBruto": 3200.00,
  "tonelajeNeto": 1700.00,
  "eslora": 78.00,
  "manga": 13.00,
  "puntal": 6.50,
  "anioConstruccion": 2017,
  "lugarConstruccion": "Astillero Balboa",
  "materialCasco": "Acero",
  "tipoPropulsion": "Diesel",
  "potenciaKw": 2100.00
}""")
}


/**
 * Caso auxiliar, NO va en ninguna suite.
 *
 * Un segundo agente, nuevo en cada corrida, para probar el aislamiento
 * entre agentes sin depender de usuarios de demo. Valida el correo con el
 * enlace que llega a Mailpit (http://localhost:8025, docker compose).
 */
String correo = 'katalon.s5.' + System.currentTimeMillis() + '@navesitas.pa'

ResponseObject r = postJson(null, '/api/auth/registro', """{
  "correo": "${correo}",
  "password": "Navesitas2026*",
  "nombreCompleto": "Segundo Agente Katalon",
  "rol": "AGENTE_NAVIERO"
}""")
WS.verifyResponseStatusCode(r, 201)

// El correo de validacion tarda un instante en llegar a Mailpit.
String enlace = null
for (int intento = 0; intento < 10 && !enlace; intento++) {
    RequestObject busqueda = new RequestObject('mailpitBuscar')
    busqueda.setRestUrl('http://localhost:8025/api/v1/search?query=' + URLEncoder.encode('to:' + correo, 'UTF-8'))
    busqueda.setRestRequestMethod('GET')
    def mensajes = json(Http.enviar(busqueda)).messages
    if (mensajes) {
        RequestObject mensaje = new RequestObject('mailpitMensaje')
        mensaje.setRestUrl('http://localhost:8025/api/v1/message/' + mensajes[0].ID)
        mensaje.setRestRequestMethod('GET')
        def m = (json(Http.enviar(mensaje)).Text =~ /https?:\/\/\S*validar\?token=[\w-]+/)
        enlace = m.find() ? m.group(0) : null
    }
    if (!enlace) Thread.sleep(500)
}
assert enlace : 'No llego a Mailpit el correo de validacion de ' + correo

RequestObject validar = new RequestObject('validar')
validar.setRestUrl(enlace)
validar.setRestRequestMethod('GET')
Http.enviar(validar)

String cookie = iniciarSesion(correo)
assert cookie : 'No se pudo iniciar sesion con ' + correo
// Con sesion valida, la lista de naves responde 200 (vacia).
WS.verifyResponseStatusCode(consultar(cookie, '/api/naves'), 200)

println('Segundo agente: ' + correo)
return [correo: correo, cookie: cookie]
