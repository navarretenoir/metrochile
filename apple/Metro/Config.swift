// Metro Chile para macOS e iOS — configuración del anfitrión. Desarrollado por Javiera Navarrete.
import Foundation
extension ConfigApp {
    static let actual = ConfigApp(
        nombre: "Metro Chile",
        inicio: .local("index.html"),
        puente: .metro,                       // ui/app.js ve webkit.messageHandlers.bridge (como en Linux)
        permitidos: ["https://www.metro.cl/", "https://api.open-meteo.com/"],
        fuera: ["openstreetmap.org", "google.com/maps", "maps.apple.com"],
        ventanasDentro: false,
        activos: [])
}
