# tsf auth mod

[![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.11-blue?logo=minecraft&style=for-the-badge)](https://fabricmc.net/)
[![Platform](https://img.shields.io/badge/Platform-Fabric-orange?style=for-the-badge)](https://fabricmc.net/)
[![Java Version](https://img.shields.io/badge/Java-21-red?logo=openjdk&style=for-the-badge)](https://adoptium.net/)
[![Fabric Loader](https://img.shields.io/badge/Loader-Fabric--Loader%20%3E%3D0.19.2-green?style=for-the-badge)](https://fabricmc.net/)

Клиентский Fabric-мод для бесшовного управления несколькими игровыми аккаунтами, авторизации через собственный сервер авторизации (`tsf-auth`) и обеспечения безопасности на стороне игрового клиента.

---

## 🚀 Основной функционал

### 🔄 Менеджер аккаунтов (Account Switcher)
* **Интерфейс**: Встроенный кастомный GUI-экран (`AccountListScreen`), позволяющий добавлять, переключать и удалять аккаунты прямо внутри запущенного клиента игры без её перезапуска.
* **Авторизация**: Полная интеграция с Authlib Injector для поддержки авторизации на приватном сервере TSF.

### 🛡️ Фоновый авторефрешер (`TokenRefresher`)
* **Автоматическое продление сессии**: Запускает периодическую фоновую задачу для валидации авторизационных токенов. Токены автоматически валидируются на сервере авторизации в фоновом режиме, тем самым продлевая срок их действия и предотвращая вылеты из сессий во время долгого отсутствия активности.

---

## ⚙️ Системные требования
* **Minecraft**: Версия `1.21.11` (совместимо с Fabric API).
* **Fabric Loader**: Версия `0.19.2` or higher.
* **Java**: **JDK 21** or higher.
