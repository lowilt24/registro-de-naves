package navesitas

import com.kms.katalon.core.testobject.ConditionType
import com.kms.katalon.core.testobject.RequestObject
import com.kms.katalon.core.testobject.ResponseObject
import com.kms.katalon.core.testobject.TestObjectProperty
import com.kms.katalon.core.webservice.keyword.WSBuiltInKeywords as WS

/**
 * Sprint 6 (T6): envio de peticiones con el token CSRF de SEC-05.
 *
 * Desde SEC-05 todo POST a la app necesita la cookie XSRF-TOKEN y el mismo
 * valor en el header X-XSRF-TOKEN. Los casos usan Http.enviar en lugar de
 * WS.sendRequest: a los POST a la app les agrega un token nuevo, pedido con
 * un GET publico; los GET y las peticiones a Mailpit pasan sin cambios.
 *
 * Un caso que quiera probar un POST sin token usa WS.sendRequest directo.
 *
 * El login responde con dos Set-Cookie: XSRF-TOKEN vacia (Spring rota el
 * token al autenticar) y JSESSIONID. Por eso la sesion se busca por nombre
 * con Http.sesion y no se toma la primera cookie.
 */
class Http {

    static final String APP = 'http://localhost:8080'

    private static final List<String> SEGUROS = ['GET', 'HEAD', 'OPTIONS', 'TRACE']

    /** WS.sendRequest con el token CSRF en los POST a la app. */
    static ResponseObject enviar(RequestObject peticion) {
        String metodo = peticion.getRestRequestMethod()?.toUpperCase()
        if (peticion.getRestUrl()?.startsWith(APP) && !(metodo in SEGUROS)) {
            agregarToken(peticion)
        }
        return WS.sendRequest(peticion)
    }

    /** "JSESSIONID=..." de la respuesta del login, o null si no hay sesion. */
    static String sesion(ResponseObject respuesta) {
        return cookie(respuesta, 'JSESSIONID')
    }

    /** Cookie + header con el mismo token. Respeta un X-XSRF-TOKEN puesto por el caso. */
    private static void agregarToken(RequestObject peticion) {
        List<TestObjectProperty> cabeceras = new ArrayList<>(peticion.getHttpHeaderProperties() ?: [])
        if (cabeceras.any { it.getName().equalsIgnoreCase('X-XSRF-TOKEN') }) {
            return
        }
        String token = tokenNuevo()
        String previa = cabeceras.find { it.getName().equalsIgnoreCase('Cookie') }?.getValue()
        String valor = previa ? previa + '; XSRF-TOKEN=' + token : 'XSRF-TOKEN=' + token
        cabeceras.removeAll { it.getName().equalsIgnoreCase('Cookie') }
        cabeceras << new TestObjectProperty('Cookie', ConditionType.EQUALS, valor)
        cabeceras << new TestObjectProperty('X-XSRF-TOKEN', ConditionType.EQUALS, token)
        peticion.setHttpHeaderProperties(cabeceras)
    }

    /** Cualquier GET publico sin cookies hace que el servidor entregue XSRF-TOKEN. */
    private static String tokenNuevo() {
        RequestObject get = new RequestObject('csrf')
        get.setRestUrl(APP + '/actuator/health')
        get.setRestRequestMethod('GET')
        get.setHttpHeaderProperties([])
        String par = cookie(WS.sendRequest(get), 'XSRF-TOKEN')
        if (!par) {
            throw new IllegalStateException('La app no entrego la cookie XSRF-TOKEN (SEC-05)')
        }
        return par.substring('XSRF-TOKEN='.length())
    }

    /** "nombre=valor" del primer Set-Cookie con ese nombre y valor no vacio. */
    private static String cookie(ResponseObject respuesta, String nombre) {
        List<String> valores = respuesta.getHeaderFields()
                .find { k, v -> k?.equalsIgnoreCase('Set-Cookie') }?.value ?: []
        String linea = valores.find { it.startsWith(nombre + '=') && !it.startsWith(nombre + '=;') }
        return linea?.split(';')?.getAt(0)
    }
}
