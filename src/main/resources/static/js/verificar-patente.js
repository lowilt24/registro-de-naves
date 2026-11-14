/* Consulta pública independiente de la sesión y de las API del expediente. */
(async () => {
  const aviso = document.getElementById('verificacion-estado');
  const id = window.location.hash.slice(1);
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id)) {
    aviso.textContent = 'El enlace de verificación no es válido.';
    return;
  }
  try {
    const respuesta = await fetch(new URL('api/verificacion/patentes/' + encodeURIComponent(id), window.location.href), {
      credentials: 'omit', cache: 'no-store', referrerPolicy: 'no-referrer'
    });
    if (respuesta.status === 404) {
      aviso.textContent = 'No se encontró una patente con este identificador. No se puede confirmar su autenticidad.';
      return;
    }
    if (!respuesta.ok) throw new Error('consulta');
    const datos = await respuesta.json();
    document.getElementById('numero').textContent = datos.numero;
    document.getElementById('emision').textContent = datos.emitidaEn;
    document.getElementById('estado').textContent = datos.estado;
    document.getElementById('vigencia').textContent = datos.vigencia;
    document.getElementById('verificacion-datos').classList.remove('oculto');
    aviso.textContent = 'Emisión encontrada en Registro de Naves. Compare el número y la fecha con el documento.';
  } catch (e) {
    aviso.textContent = 'No fue posible consultar el sistema. Intente nuevamente; no se ha confirmado la autenticidad.';
  }
})();
