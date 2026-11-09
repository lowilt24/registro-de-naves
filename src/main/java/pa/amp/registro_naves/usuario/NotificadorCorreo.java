package pa.amp.registro_naves.usuario;

/**
 * Envio del enlace de validacion de correo.
 *
 * Es una interfaz para que las pruebas puedan capturar el enlace sin un
 * servidor de correo, y para que el envio real (SMTP) se pueda cambiar
 * sin tocar UsuarioService.
 */
public interface NotificadorCorreo {

    /**
     * No debe lanzar excepciones: si el correo no sale, el registro igual
     * queda hecho y el usuario puede pedir el reenvio.
     */
    void enviarValidacion(String destinatario, String nombre, String enlace);

    /** HU-16. Puede fallar: el listener posterior al commit registra el fallo. */
    void enviarAviso(String destinatario, String asunto, String texto);
}
