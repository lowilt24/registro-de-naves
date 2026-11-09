/* HU-15: descarga binaria autenticada; el servidor decide estado y permisos. */
function actualizarConstancia(estado) {
  $('constancia-registro').classList.toggle('oculto', estado !== 'APROBADA');
  ocultar($('aviso-constancia'));
}

$('btn-constancia').addEventListener('click', async () => {
  const boton = $('btn-constancia');
  if (boton.disabled) return;
  boton.disabled = true;
  const aviso = $('aviso-constancia');
  ocultar(aviso);
  try {
    const respuesta = await fetch('/api/naves/' + encodeURIComponent(naveId) + '/constancia', {
      credentials: 'same-origin', headers: { Accept: 'application/pdf, application/json' }
    });
    if (respuesta.status === 401) {
      location.href = '/login.html?expirada=1';
      return;
    }
    if (!respuesta.ok) {
      const datos = await respuesta.json().catch(() => null);
      mostrar(aviso, 'ocupado', datos?.mensaje || 'No se pudo descargar la constancia. Intente de nuevo.');
      return;
    }
    if (!respuesta.headers.get('Content-Type')?.includes('application/pdf')) {
      throw new Error('Respuesta sin PDF');
    }
    const archivo = await respuesta.blob();
    const url = URL.createObjectURL(archivo);
    const enlace = document.createElement('a');
    enlace.href = url;
    enlace.download = 'constancia-registro-' + naveId + '.pdf';
    document.body.appendChild(enlace);
    enlace.click();
    enlace.remove();
    // Se conserva la URL hasta que el navegador haya iniciado la descarga.
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    mostrar(aviso, 'libre', 'Constancia de registro descargada.');
  } catch (error) {
    mostrar(aviso, 'ocupado', 'No se pudo descargar la constancia. Revise la conexión e intente de nuevo.');
  } finally {
    boton.disabled = false;
  }
});
