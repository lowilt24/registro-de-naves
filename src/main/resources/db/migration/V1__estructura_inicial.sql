CREATE TABLE solicitud (
                           id              BIGINT AUTO_INCREMENT PRIMARY KEY,
                           tipo_tramite    VARCHAR(50) NOT NULL,
                           estado          VARCHAR(30) NOT NULL DEFAULT 'BORRADOR',
                           fecha_creacion  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);