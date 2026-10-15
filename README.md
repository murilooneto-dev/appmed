# AppMed

App Android simples para lembrete de medicamentos.

## Como funciona

1. **Cadastro** – o usuário informa:
   - nome do medicamento;
   - quantidade por dose (ex.: "1 comprimido", "10 gotas");
   - frequência (a cada X horas — atalhos para 4h, 6h, 8h, 12h e 24h);
   - duração do tratamento em dias;
   - dia e hora em que tomou a primeira dose.
2. **Rotina automática** – ao salvar, o app gera todos os horários do tratamento
   (primeira dose + intervalo, até completar a duração) e agenda um **alarme** para
   cada dose. A primeira dose já fica registrada como tomada.
3. **Alarme** – no horário, o celular toca (som de alarme repetido + vibração) e
   mostra uma tela cheia, mesmo bloqueado, com os botões **Tomei** e **Adiar 10 min**.
   Os mesmos botões aparecem na notificação.
4. **Acompanhamento** – a tela do medicamento lista todos os dias e horários, com
   o status de cada dose (confirmada, agendada ou não confirmada).
5. **Histórico** – ao fim do tratamento ele vai para o *Histórico*, onde fica
   registrado o dia e a hora em que cada dose foi confirmada.

Os alarmes são reagendados automaticamente após reiniciar o aparelho.

## Tecnologia

- Kotlin + Jetpack Compose (Material 3)
- SQLite local (sem internet, sem conta)
- `AlarmManager.setAlarmClock` para alarmes exatos, que funcionam com o modo de economia
- Android 8.0 (API 26) ou superior

## Gerar o APK

**Pelo GitHub:** cada push roda o workflow *Build APK* (aba **Actions**). Abra a
execução e baixe o artefato `appmed-debug-apk`; descompacte e instale o
`app-debug.apk` no celular (é preciso permitir "instalar apps de fontes desconhecidas").

**Localmente:** com o Android SDK instalado (ou pelo Android Studio):

```bash
./gradlew assembleDebug
# APK em app/build/outputs/apk/debug/app-debug.apk
```

## Permissões usadas

| Permissão | Para quê |
|---|---|
| Notificações | exibir o alarme da dose |
| Alarmes exatos (`USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM`) | tocar no horário exato |
| Intenção de tela cheia | mostrar o alarme com o celular bloqueado |
| Inicialização (`RECEIVE_BOOT_COMPLETED`) | reagendar alarmes após reiniciar |
| Vibração | vibrar no alarme |
