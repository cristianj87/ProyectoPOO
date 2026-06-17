package com.mycompany.sistech.controlador;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

public class Ctrl_Usuario {

    // Centralizamos la conexión
    private final String url = "jdbc:mysql://localhost/sistemainventario";
    private final String userDB = "root";
    private final String passDB = "";

    // Método para cambiar la información del usuario (Ej. nombre y apellidos)
    public boolean actualizarDatosUsuario(String nicknameUsuarioActual, String nuevoNombre, String nuevosApellidos) {
        
        // El comando UPDATE modifica solo la fila donde el nickname coincida
        String sql = "UPDATE usuarios SET nombre = ?, apellidos = ? WHERE nickname = ?";

        try (Connection conn = DriverManager.getConnection(url, userDB, passDB);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // Asignamos los nuevos valores
            pstmt.setString(1, nuevoNombre);
            pstmt.setString(2, nuevosApellidos);
            
            // Le decimos a qué usuario se le aplicarán los cambios
            pstmt.setString(3, nicknameUsuarioActual);

            // executeUpdate() devuelve cuántas filas se modificaron
            int filasAfectadas = pstmt.executeUpdate();
            return filasAfectadas > 0;

        } catch (Exception e) {
            System.out.println("Error al actualizar usuario: " + e.getMessage());
            return false;
        }
    }
    
    // Aquí también podrías agregar otros métodos en el futuro, como:
    // public boolean cambiarPassword(String nickname, String nuevaPasswordCifrada) { ... }
    // public boolean eliminarUsuario(String nickname) { ... }
}