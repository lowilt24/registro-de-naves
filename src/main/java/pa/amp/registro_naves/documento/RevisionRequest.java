package pa.amp.registro_naves.documento;

/**
 * Cuerpo de POST /api/naves/{naveId}/documentos/{documentoId}/revision.
 *
 * Los dos campos llegan como texto y los valida el servicio, no Bean
 * Validation ni Jackson: asi el 400 por un campo sale despues del 404, el
 * 403 y el 409 (orden de HU-12 aceptado por la PO: 401 -> 404 -> 403 ->
 * 409 -> 400, igual que HU-09). Solo un JSON roto da 400 antes, porque no
 * se puede leer.
 */
public record RevisionRequest(String decision, String observacion) {
}
