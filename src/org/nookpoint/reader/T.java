package org.nookpoint.reader;

import java.util.HashMap;
import java.util.Locale;

/** Interface translations. The English text is the key; {0}, {1} are placeholders. */
class T {
    private static int idx;            // 0 en, 1 ru, 2 fr, 3 es
    private static String code = "en";
    private static HashMap<String, String[]> map;

    static void reload() {
        String l = Prefs.get("lang", "Auto");
        String c;
        if (l.equals("\u0420\u0443\u0441\u0441\u043A\u0438\u0439")) c = "ru";
        else if (l.equals("Fran\u00E7ais")) c = "fr";
        else if (l.equals("Espa\u00F1ol")) c = "es";
        else if (l.equals("English")) c = "en";
        else {
            c = Locale.getDefault().getLanguage();
            if (!c.equals("ru") && !c.equals("fr") && !c.equals("es")) c = "en";
        }
        code = c;
        idx = c.equals("ru") ? 1 : (c.equals("fr") ? 2 : (c.equals("es") ? 3 : 0));
    }

    static String code() { return code; }
    static Locale locale() { return new Locale(code); }

    static String t(String key) {
        if (idx == 0) return key;
        if (map == null) build();
        String[] r = map.get(key);
        return r == null ? key : r[idx - 1];
    }

    static String f(String key, Object... a) {
        String s = t(key);
        for (int i = 0; i < a.length; i++) s = s.replace("{" + i + "}", String.valueOf(a[i]));
        return s;
    }

    private static final String[][] DAYS = {
        {"Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"},
        {"Вс", "Пн", "Вт", "Ср", "Чт", "Пт", "Сб"},
        {"dim", "lun", "mar", "mer", "jeu", "ven", "sam"},
        {"dom", "lun", "mar", "mié", "jue", "vie", "sáb"}};
    private static final String[][] MONTHS = {
        {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"},
        {"янв", "фев", "мар", "апр", "мая", "июн", "июл", "авг", "сен", "окт", "ноя", "дек"},
        {"janv.", "févr.", "mars", "avr.", "mai", "juin", "juil.", "août", "sept.", "oct.", "nov.", "déc."},
        {"ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"}};

    /** Short weekday name; dow is Calendar.DAY_OF_WEEK (1 = Sunday). Own tables: old Android has no locale data. */
    static String dayShort(int dow) { return DAYS[idx][(dow - 1 + 7) % 7]; }

    static String dateShort(java.util.Date d) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTime(d);
        return c.get(java.util.Calendar.DAY_OF_MONTH) + " " + MONTHS[idx][c.get(java.util.Calendar.MONTH)];
    }

    static String days(int n) {
        if (idx == 1) {
            int m10 = n % 10, m100 = n % 100;
            if (m10 == 1 && m100 != 11) return "\u0434\u0435\u043D\u044C";
            if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return "\u0434\u043D\u044F";
            return "\u0434\u043D\u0435\u0439";
        }
        if (idx == 2) return n <= 1 ? "jour" : "jours";
        if (idx == 3) return n == 1 ? "d\u00EDa" : "d\u00EDas";
        return n == 1 ? "day" : "days";
    }

    static String books(int n) {
        if (idx == 1) {
            int m10 = n % 10, m100 = n % 100;
            if (m10 == 1 && m100 != 11) return "\u043A\u043D\u0438\u0433\u0430";
            if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return "\u043A\u043D\u0438\u0433\u0438";
            return "\u043A\u043D\u0438\u0433";
        }
        if (idx == 2) return n <= 1 ? "livre" : "livres";
        if (idx == 3) return n == 1 ? "libro" : "libros";
        return n == 1 ? "book" : "books";
    }

    private static void build() {
        map = new HashMap<String, String[]>();
        for (int i = 0; i < ROWS.length; i++) map.put(ROWS[i][0], new String[]{ROWS[i][1], ROWS[i][2], ROWS[i][3]});
    }

    private static final String[][] ROWS = {
        // ---- home / library
        {"Library", "Библиотека", "Bibliothèque", "Biblioteca"},
        {"Stats", "Статистика", "Stats", "Estadísticas"},
        {"Settings", "Настройки", "Réglages", "Ajustes"},
        {"Today", "Сегодня", "Aujourd'hui", "Hoy"},
        {"goal reached", "цель достигнута", "objectif atteint", "meta cumplida"},
        {"min", "мин", "min", "min"},
        {"h", "ч", "h", "h"},
        {"m", "м", "min", "min"},
        {"Welcome!\n\nNo books yet.\nOpen Wi-Fi to upload books from your phone or computer, or Library to browse the card.",
         "Добро пожаловать!\n\nКниг пока нет.\nОткройте Wi-Fi, чтобы загрузить книги с телефона или компьютера, либо Библиотеку, чтобы просмотреть карту памяти.",
         "Bienvenue !\n\nAucun livre pour l'instant.\nOuvrez Wi-Fi pour envoyer des livres depuis votre téléphone ou ordinateur, ou Bibliothèque pour parcourir la carte.",
         "¡Bienvenido!\n\nAún no hay libros.\nAbre Wi-Fi para subir libros desde tu teléfono u ordenador, o Biblioteca para explorar la tarjeta."},
        {"RECENT", "НЕДАВНИЕ", "RÉCENTS", "RECIENTES"},
        {"CONTINUE READING", "ПРОДОЛЖИТЬ ЧТЕНИЕ", "CONTINUER LA LECTURE", "CONTINUAR LEYENDO"},
        {"Finished", "Прочитано", "Terminé", "Terminado"},
        {"New", "Новая", "Nouveau", "Nuevo"},
        {"{0} read", "прочитано: {0}", "{0} de lecture", "{0} de lectura"},
        {"No books here.\nUse Wi-Fi transfer to add some.",
         "Здесь нет книг.\nДобавьте их через передачу по Wi-Fi.",
         "Aucun livre ici.\nUtilisez le transfert Wi-Fi pour en ajouter.",
         "No hay libros aquí.\nUsa la transferencia Wi-Fi para añadirlos."},

        // ---- reader
        {"Cannot open book: {0}", "Не удалось открыть книгу: {0}", "Impossible d'ouvrir le livre : {0}", "No se puede abrir el libro: {0}"},
        {"Error reading this section", "Ошибка чтения раздела", "Erreur de lecture de cette section", "Error al leer esta sección"},
        {"Book finished!", "Книга прочитана!", "Livre terminé !", "¡Libro terminado!"},
        {"{0} of {1} this year", "{0} из {1} в этом году", "{0} sur {1} cette année", "{0} de {1} este año"},
        {"Daily goal reached: {0} min. Well done!", "Дневная цель достигнута: {0} мин. Отлично!", "Objectif quotidien atteint : {0} min. Bravo !", "Meta diaria cumplida: {0} min. ¡Bien hecho!"},
        {"End of book", "Конец книги", "Fin du livre", "Fin del libro"},
        {"External link: {0}", "Внешняя ссылка: {0}", "Lien externe : {0}", "Enlace externo: {0}"},
        {"Link target not found", "Цель ссылки не найдена", "Cible du lien introuvable", "Destino del enlace no encontrado"},
        {"Note", "Примечание", "Note", "Nota"},
        {"Close", "Закрыть", "Fermer", "Cerrar"},
        {"Go there", "Перейти", "Y aller", "Ir allí"},
        {"Back button returns here", "Кнопка «Назад» вернёт сюда", "Le bouton Retour revient ici", "El botón Atrás vuelve aquí"},
        {"Bookmark added", "Закладка добавлена", "Signet ajouté", "Marcador añadido"},
        {"Bookmark removed", "Закладка удалена", "Signet supprimé", "Marcador eliminado"},
        {"Back to previous place", "Назад на прежнее место", "Retour à l'endroit précédent", "Volver al lugar anterior"},
        {"Contents", "Оглавление", "Table des matières", "Contenido"},
        {"Bookmarks", "Закладки", "Signets", "Marcadores"},
        {"Add bookmark", "Добавить закладку", "Ajouter un signet", "Añadir marcador"},
        {"Remove bookmark", "Удалить закладку", "Supprimer le signet", "Quitar marcador"},
        {"Dictionary", "Словарь", "Dictionnaire", "Diccionario"},
        {"Text size +", "Размер текста +", "Taille du texte +", "Tamaño del texto +"},
        {"Text size -", "Размер текста −", "Taille du texte −", "Tamaño del texto −"},
        {"Statistics", "Статистика", "Statistiques", "Estadísticas"},
        {"Back to library", "В библиотеку", "Retour à la bibliothèque", "Volver a la biblioteca"},
        {"Section {0}", "Раздел {0}", "Section {0}", "Sección {0}"},
        {"No bookmarks yet.\nTap the top-right corner of a page to add one.",
         "Закладок пока нет.\nКоснитесь правого верхнего угла страницы, чтобы добавить.",
         "Aucun signet.\nTouchez le coin supérieur droit d'une page pour en ajouter un.",
         "Aún no hay marcadores.\nToca la esquina superior derecha de una página para añadir uno."},

        // ---- shelves, release
        {"Shelves", "Полки", "Étagères", "Estantes"},
        {"SHELVES", "ПОЛКИ", "ÉTAGÈRES", "ESTANTES"},
        {"MY SHELVES", "МОИ ПОЛКИ", "MES ÉTAGÈRES", "MIS ESTANTES"},
        {"AUTOMATIC", "АВТОМАТИЧЕСКИЕ", "AUTOMATIQUES", "AUTOMÁTICAS"},
        {"SERIES", "СЕРИИ", "SÉRIES", "SERIES"},
        {"All shelves", "Все полки", "Toutes les étagères", "Todas las estantes"},
        {"Rename", "Переименовать", "Renommer", "Renombrar"},
        {"\u25A4 Shelf", "\u25A4 Полка", "\u25A4 Étagère", "\u25A4 Estante"},
        {"Delete the shelf \"{0}\"? The books stay in the library.", "Удалить полку «{0}»? Книги останутся в библиотеке.", "Supprimer l'étagère « {0} » ? Les livres restent dans la bibliothèque.", "¿Eliminar la estante «{0}»? Los libros permanecen en la biblioteca."},
        {"What's new in {0}", "Что нового в {0}", "Nouveautés de la version {0}", "Novedades de la versión {0}"},
        {"WHATS_NEW_BODY",
         "\u2022 Наглядные полки: обложки стоят на досках, экран «Полки» с плитками, вид «Полка» в библиотеке и полки на главном экране\n\u2022 Книги FB2, картинки в книгах, поиск по тексту, выделения и заметки\n\u2022 Время до конца главы, сохранённые слова, серии\n\u2022 Онлайн-каталоги OPDS (например, ваш сервер Calibre)\n\u2022 Точные переносы слов, резервные копии и журнал ошибок в настройках",
         "\u2022 Étagères visuelles : couvertures posées sur des planches, écran Étagères avec vignettes, vue Étagère dans la bibliothèque et étagères sur l'écran d'accueil\n\u2022 Livres FB2, images dans les livres, recherche dans le texte, surlignages et notes\n\u2022 Temps restant du chapitre, mots enregistrés, séries\n\u2022 Catalogues en ligne OPDS (par exemple votre serveur Calibre)\n\u2022 Césure précise, sauvegardes et journal des erreurs dans les réglages",
         "\u2022 Estantes visuales: portadas sobre tablas, pantalla Estantes con miniaturas, vista Estante en la biblioteca y estantes en la pantalla principal\n\u2022 Libros FB2, imágenes en los libros, búsqueda en el texto, subrayados y notas\n\u2022 Tiempo restante del capítulo, palabras guardadas, series\n\u2022 Catálogos en línea OPDS (por ejemplo tu servidor Calibre)\n\u2022 Separación silábica precisa, copias de seguridad y registro de errores en los ajustes"},

        // ---- backup, log, online catalogs, shelves
        {"Back up data", "Сохранить данные", "Sauvegarder les données", "Copia de seguridad"},
        {"Restore data", "Восстановить данные", "Restaurer les données", "Restaurar datos"},
        {"Show error log", "Показать журнал ошибок", "Afficher le journal des erreurs", "Mostrar registro de errores"},
        {"Backup saved: {0}", "Копия сохранена: {0}", "Sauvegarde enregistrée : {0}", "Copia guardada: {0}"},
        {"No backups found.", "Резервных копий нет.", "Aucune sauvegarde trouvée.", "No se encontraron copias."},
        {"Restore this backup? Current settings, positions and notes will be replaced.", "Восстановить эту копию? Текущие настройки, позиции чтения и заметки будут заменены.", "Restaurer cette sauvegarde ? Les réglages, positions et notes actuels seront remplacés.", "¿Restaurar esta copia? Se reemplazarán los ajustes, posiciones y notas actuales."},
        {"Restored {0} entries.", "Восстановлено записей: {0}.", "{0} entrées restaurées.", "{0} entradas restauradas."},
        {"The log is empty.", "Журнал пуст.", "Le journal est vide.", "El registro está vacío."},
        {"Clear", "Очистить", "Effacer", "Borrar"},
        {"Online", "Онлайн", "En ligne", "En línea"},
        {"Online catalogs", "Онлайн-каталоги", "Catalogues en ligne", "Catálogos en línea"},
        {"Add catalog", "Добавить каталог", "Ajouter un catalogue", "Añadir catálogo"},
        {"Catalog", "Каталог", "Catalogue", "Catálogo"},
        {"Catalogs", "Каталоги", "Catalogues", "Catálogos"},
        {"Name", "Название", "Nom", "Nombre"},
        {"Address (URL)", "Адрес (URL)", "Adresse (URL)", "Dirección (URL)"},
        {"Login (optional)", "Логин (необязательно)", "Identifiant (facultatif)", "Usuario (opcional)"},
        {"Password (optional)", "Пароль (необязательно)", "Mot de passe (facultatif)", "Contraseña (opcional)"},
        {"No catalogs yet.\nTap Add catalog. Example for a Calibre server on your home network: http://192.168.1.10:8080/opds",
         "Каталогов пока нет.\nНажмите «Добавить каталог». Пример для сервера Calibre в домашней сети: http://192.168.1.10:8080/opds",
         "Aucun catalogue.\nTouchez Ajouter un catalogue. Exemple pour un serveur Calibre sur votre réseau : http://192.168.1.10:8080/opds",
         "Aún no hay catálogos.\nToca Añadir catálogo. Ejemplo para un servidor Calibre en tu red: http://192.168.1.10:8080/opds"},
        {"This old Android may not support secure (https) connections. Try an http:// address on your home network.",
         "Этот старый Android может не поддерживать защищённые (https) соединения. Попробуйте адрес http:// в домашней сети.",
         "Ce vieil Android ne prend peut-être pas en charge les connexions sécurisées (https). Essayez une adresse http:// sur votre réseau local.",
         "Este Android antiguo puede no admitir conexiones seguras (https). Prueba una dirección http:// en tu red local."},
        {"Next page", "Следующая страница", "Page suivante", "Página siguiente"},
        {"This catalog does not support search.", "Этот каталог не поддерживает поиск.", "Ce catalogue ne prend pas en charge la recherche.", "Este catálogo no admite búsqueda."},
        {"No supported format (EPUB, FB2) is offered for this book.", "Для этой книги нет поддерживаемого формата (EPUB, FB2).", "Aucun format pris en charge (EPUB, FB2) pour ce livre.", "No hay un formato compatible (EPUB, FB2) para este libro."},
        {"Download", "Скачать", "Télécharger", "Descargar"},
        {"Saved: {0}", "Сохранено: {0}", "Enregistré : {0}", "Guardado: {0}"},
        {"Series", "Серия", "Série", "Serie"},
        {"Shelves...", "Полки...", "Étagères...", "Estantes..."},
        {"Series...", "Серии...", "Séries...", "Series..."},
        {"New shelf", "Новая полка", "Nouvelle étagère", "Nueva estante"},
        {"Show all books", "Показать все книги", "Afficher tous les livres", "Mostrar todos los libros"},
        {"Delete this shelf", "Удалить эту полку", "Supprimer cette étagère", "Eliminar esta estante"},
        {"Add to shelf", "Добавить на полку", "Ajouter à une étagère", "Añadir a una estante"},
        {"Shelf: {0}", "Полка: {0}", "Étagère : {0}", "Estante: {0}"},
        {"Series: {0}", "Серия: {0}", "Série : {0}", "Serie: {0}"},
        {"No series found.", "Серий не найдено.", "Aucune série trouvée.", "No se encontraron series."},

        // ---- highlights, search, saved words, reading options
        {"Highlights", "Выделения", "Surlignages", "Subrayados"},
        {"Search in book", "Поиск по книге", "Rechercher dans le livre", "Buscar en el libro"},
        {"Saved words", "Сохранённые слова", "Mots enregistrés", "Palabras guardadas"},
        {"More", "Ещё", "Plus", "Más"},
        {"Save", "Сохранить", "Enregistrer", "Guardar"},
        {"Highlight sentence", "Выделить предложение", "Surligner la phrase", "Subrayar la oración"},
        {"Highlight paragraph", "Выделить абзац", "Surligner le paragraphe", "Subrayar el párrafo"},
        {"Highlight with note", "Выделить с заметкой", "Surligner avec une note", "Subrayar con nota"},
        {"Find \"{0}\" in the book", "Найти «{0}» в книге", "Chercher « {0} » dans le livre", "Buscar «{0}» en el libro"},
        {"Highlight added", "Выделение добавлено", "Surlignage ajouté", "Subrayado añadido"},
        {"Searching...", "Идёт поиск...", "Recherche en cours...", "Buscando..."},
        {"No matches.", "Совпадений нет.", "Aucun résultat.", "Sin coincidencias."},
        {"Export", "Экспорт", "Exporter", "Exportar"},
        {"Exported to {0}", "Сохранено в {0}", "Exporté dans {0}", "Exportado en {0}"},
        {"Go to", "Перейти", "Aller à", "Ir a"},
        {"Edit note", "Изменить заметку", "Modifier la note", "Editar nota"},
        {"No highlights yet.\nLong-press a word and choose Highlight.", "Выделений пока нет.\nУдерживайте слово и выберите «Выделить».", "Aucun surlignage.\nMaintenez un mot et choisissez Surligner.", "Aún no hay subrayados.\nMantén pulsada una palabra y elige Subrayar."},
        {"No saved words yet.\nWords you look up in the dictionary are saved here.", "Сохранённых слов пока нет.\nСлова, которые вы ищете в словаре, сохраняются здесь.", "Aucun mot enregistré.\nLes mots cherchés dans le dictionnaire sont enregistrés ici.", "Aún no hay palabras guardadas.\nLas palabras que buscas en el diccionario se guardan aquí."},
        {"Clear all", "Очистить всё", "Tout effacer", "Borrar todo"},
        {"Delete all saved words?", "Удалить все сохранённые слова?", "Supprimer tous les mots enregistrés ?", "¿Eliminar todas las palabras guardadas?"},
        {"Time left in status bar", "Время до конца в строке состояния", "Temps restant dans la barre d'état", "Tiempo restante en la barra de estado"},
        {"Text weight", "Насыщенность текста", "Épaisseur du texte", "Grosor del texto"},
        {"Long press on a word", "Долгое нажатие на слово", "Appui long sur un mot", "Pulsación larga en una palabra"},
        {"Save looked-up words", "Сохранять найденные слова", "Enregistrer les mots recherchés", "Guardar las palabras buscadas"},
        {"Normal", "Обычная", "Normale", "Normal"},
        {"Bold", "Жирная", "Gras", "Negrita"},
        {"Chapter", "Глава", "Chapitre", "Capítulo"},
        {"Book", "Книга", "Livre", "Libro"},
        {"Menu", "Меню", "Menu", "Menú"},

        // ---- pictures, notes, backups
        {"Tap: zoom   Hold: rotate   Back: close", "Касание: масштаб   Удержание: поворот   Назад: закрыть", "Toucher : zoom   Maintenir : rotation   Retour : fermer", "Tocar: zoom   Mantener: girar   Atrás: cerrar"},
        {"Notes", "Примечания", "Notes", "Notas"},
        {"Backups", "Резервные копии", "Sauvegardes", "Copias de seguridad"},
        {"Notes and words", "Заметки и слова", "Notes et mots", "Notas y palabras"},
        {"Error log", "Журнал ошибок", "Journal des erreurs", "Registro de errores"},

        // ---- book info / dictionary errors
        {"Book info", "О книге", "Infos sur le livre", "Información del libro"},
        {"No description.", "Описания нет.", "Pas de description.", "Sin descripción."},
        {"Dictionary error: {0}", "Ошибка словаря: {0}", "Erreur du dictionnaire : {0}", "Error del diccionario: {0}"},

        // ---- library
        {"Search library", "Поиск по библиотеке", "Rechercher dans la bibliothèque", "Buscar en la biblioteca"},
        {"Sort", "Сортировка", "Tri", "Orden"},
        {"Filter", "Фильтр", "Filtre", "Filtro"},
        {"Sort by", "Сортировать по", "Trier par", "Ordenar por"},
        {"Show", "Показывать", "Afficher", "Mostrar"},
        {"Title A-Z", "Название А–Я", "Titre A–Z", "Título A–Z"},
        {"Author", "Автор", "Auteur", "Autor"},
        {"Recently read", "Недавно читал", "Lus récemment", "Leídos recientemente"},
        {"Progress", "Прогресс", "Progression", "Progreso"},
        {"Date added", "Дата добавления", "Date d'ajout", "Fecha de adición"},
        {"All books", "Все книги", "Tous les livres", "Todos los libros"},
        {"Reading", "Читаю", "En cours", "Leyendo"},
        {"\u2630 List", "\u2630 Список", "\u2630 Liste", "\u2630 Lista"},
        {"\u25A6 Grid", "\u25A6 Сетка", "\u25A6 Grille", "\u25A6 Cuadrícula"},
        {"Open", "Открыть", "Ouvrir", "Abrir"},
        {"Mark as read", "Отметить прочитанной", "Marquer comme lu", "Marcar como leído"},
        {"Mark as unread", "Отметить непрочитанной", "Marquer comme non lu", "Marcar como no leído"},
        {"Delete \"{0}\" from the card?", "Удалить «{0}» с карты памяти?", "Supprimer « {0} » de la carte ?", "¿Eliminar «{0}» de la tarjeta?"},
        {"No books found.\nUse Wi-Fi transfer to add some.", "Книги не найдены.\nДобавьте их через передачу по Wi-Fi.", "Aucun livre trouvé.\nUtilisez le transfert Wi-Fi pour en ajouter.", "No se encontraron libros.\nUsa la transferencia Wi-Fi para añadirlos."},
        {"Nothing matches your search.", "Ничего не найдено по запросу.", "Aucun résultat pour votre recherche.", "Nada coincide con tu búsqueda."},

        // ---- dictionary
        {"Look up word", "Найти слово", "Rechercher un mot", "Buscar palabra"},
        {"Search", "Найти", "Chercher", "Buscar"},
        {"Cancel", "Отмена", "Annuler", "Cancelar"},
        {"Not found", "Не найдено", "Introuvable", "No encontrado"},
        {"Similar:", "Похожие:", "Similaires :", "Similares:"},
        {"Preparing dictionary...", "Подготовка словаря...", "Préparation du dictionnaire...", "Preparando el diccionario..."},
        {"No dictionaries found.\n\nPut StarDict dictionaries into {0} or upload them with Wi-Fi transfer.",
         "Словари не найдены.\n\nПоложите словари StarDict в {0} или загрузите их через Wi-Fi.",
         "Aucun dictionnaire trouvé.\n\nMettez des dictionnaires StarDict dans {0} ou envoyez-les par transfert Wi-Fi.",
         "No se encontraron diccionarios.\n\nPon diccionarios StarDict en {0} o súbelos con la transferencia Wi-Fi."},
        {"Details", "Подробнее", "Détails", "Detalles"},
        {"Dictionaries", "Словари", "Dictionnaires", "Diccionarios"},
        {"INSTALLED", "УСТАНОВЛЕНО", "INSTALLÉS", "INSTALADOS"},
        {"No dictionaries found.", "Словари не найдены.", "Aucun dictionnaire trouvé.", "No se encontraron diccionarios."},
        {"{0} words", "слов: {0}", "{0} mots", "{0} palabras"},
        {"NOT USABLE", "НЕ УДАЛОСЬ ИСПОЛЬЗОВАТЬ", "INUTILISABLES", "NO UTILIZABLES"},
        {"Missing .idx file", "Нет файла .idx", "Fichier .idx manquant", "Falta el archivo .idx"},
        {"Missing .dict or .dict.dz file", "Нет файла .dict или .dict.dz", "Fichier .dict ou .dict.dz manquant", "Falta el archivo .dict o .dict.dz"},
        {"Cannot read this dictionary", "Не удалось прочитать словарь", "Impossible de lire ce dictionnaire", "No se puede leer este diccionario"},
        {"Cannot unpack the archive", "Не удалось распаковать архив", "Impossible de décompresser l'archive", "No se puede descomprimir el archivo"},
        {"The archive has no dictionary files", "В архиве нет файлов словаря", "L'archive ne contient pas de fichiers de dictionnaire", "El archivo no contiene ficheros de diccionario"},
        {"Unsupported format. Convert it to StarDict on a computer (for example with PyGlossary).",
         "Формат не поддерживается. Преобразуйте его в StarDict на компьютере (например, программой PyGlossary).",
         "Format non pris en charge. Convertissez-le en StarDict sur un ordinateur (par exemple avec PyGlossary).",
         "Formato no compatible. Conviértelo a StarDict en un ordenador (por ejemplo con PyGlossary)."},
        {"OTHER FILES IN DICTIONARY FOLDERS", "ДРУГИЕ ФАЙЛЫ В ПАПКАХ СЛОВАРЕЙ", "AUTRES FICHIERS DANS LES DOSSIERS DE DICTIONNAIRES", "OTROS ARCHIVOS EN LAS CARPETAS DE DICCIONARIOS"},
        {"FOLDERS SEARCHED", "ГДЕ ИСКАЛИ", "DOSSIERS EXAMINÉS", "CARPETAS REVISADAS"},
        {"HOW TO ADD DICTIONARIES", "КАК ДОБАВИТЬ СЛОВАРИ", "COMMENT AJOUTER DES DICTIONNAIRES", "CÓMO AÑADIR DICCIONARIOS"},
        {"Rescan", "Искать заново", "Rechercher à nouveau", "Buscar de nuevo"},
        {"Use StarDict dictionaries: a folder (or loose files) with .ifo, .idx and .dict or .dict.dz files, or a dictd pair (.index and .dict.dz). Put them into {0} or upload them with Wi-Fi transfer. Archives (.zip, .tar.gz) are unpacked automatically. Other formats (.dsl, .mdx, .bgl, .xz, .bz2) must be converted on a computer, for example with PyGlossary.",
         "Нужны словари StarDict: папка (или отдельные файлы) с .ifo, .idx и .dict или .dict.dz, либо пара dictd (.index и .dict.dz). Положите их в {0} или загрузите через Wi-Fi. Архивы (.zip, .tar.gz) распаковываются автоматически. Другие форматы (.dsl, .mdx, .bgl, .xz, .bz2) нужно преобразовать на компьютере, например программой PyGlossary.",
         "Utilisez des dictionnaires StarDict : un dossier (ou des fichiers) avec .ifo, .idx et .dict ou .dict.dz, ou une paire dictd (.index et .dict.dz). Mettez-les dans {0} ou envoyez-les par transfert Wi-Fi. Les archives (.zip, .tar.gz) sont décompressées automatiquement. Les autres formats (.dsl, .mdx, .bgl, .xz, .bz2) doivent être convertis sur un ordinateur, par exemple avec PyGlossary.",
         "Usa diccionarios StarDict: una carpeta (o archivos sueltos) con .ifo, .idx y .dict o .dict.dz, o un par dictd (.index y .dict.dz). Ponlos en {0} o súbelos con la transferencia Wi-Fi. Los archivos (.zip, .tar.gz) se descomprimen automáticamente. Otros formatos (.dsl, .mdx, .bgl, .xz, .bz2) deben convertirse en un ordenador, por ejemplo con PyGlossary."},

        // ---- settings
        {"Language", "Язык", "Langue", "Idioma"},
        {"Text size", "Размер текста", "Taille du texte", "Tamaño del texto"},
        {"Font", "Шрифт", "Police", "Fuente"},
        {"Line spacing", "Межстрочный интервал", "Interligne", "Interlineado"},
        {"Screen margin", "Поля экрана", "Marges de l'écran", "Márgenes de pantalla"},
        {"Justify text", "Выравнивание по ширине", "Texte justifié", "Texto justificado"},
        {"Paragraph indent", "Абзацный отступ", "Retrait de paragraphe", "Sangría de párrafo"},
        {"Hyphenation", "Переносы слов", "Césure", "Separación silábica"},
        {"Paragraph spacing", "Интервал между абзацами", "Espacement des paragraphes", "Espaciado de párrafos"},
        {"Underline links", "Подчёркивать ссылки", "Souligner les liens", "Subrayar enlaces"},
        {"Status bar", "Строка состояния", "Barre d'état", "Barra de estado"},
        {"Battery indicator", "Индикатор батареи", "Indicateur de batterie", "Indicador de batería"},
        {"Full refresh every N pages", "Полное обновление каждые N стр.", "Rafraîchissement complet toutes les N pages", "Actualización completa cada N páginas"},
        {"Swap page buttons", "Поменять кнопки листания", "Inverser les boutons de page", "Intercambiar botones de página"},
        {"Touch page turn (edges)", "Листание касанием (края)", "Tourner les pages au toucher (bords)", "Pasar página al tocar (bordes)"},
        {"On", "Вкл", "Activé", "Sí"},
        {"Off", "Выкл", "Désactivé", "No"},
        {"None", "Нет", "Aucun", "Ninguno"},
        {"Small", "Малый", "Petit", "Pequeño"},
        {"Medium", "Средний", "Moyen", "Medio"},
        {"Icon", "Значок", "Icône", "Icono"},
        {"Percent", "Проценты", "Pourcentage", "Porcentaje"},
        {"Auto", "Авто", "Auto", "Automático"},
        {"Key test (button codes)", "Проверка кнопок (коды)", "Test des boutons (codes)", "Prueba de botones (códigos)"},
        {"About", "О программе", "À propos", "Acerca de"},
        {"Reader for Nook Simple Touch (EPUB, FB2, TXT), inspired by CrossPoint Reader.\n\nBooks: {0}\nFonts: {1}\nDictionaries: {2}\nNotes and backups: {3}\n\nBundled fonts: Lora, IBM Plex Serif (SIL OFL), DejaVu (free license). Hyphenation patterns: TeX hyph-utf8 project (see assets/hyph).",
         "Читалка для Nook Simple Touch (EPUB, FB2, TXT) по мотивам CrossPoint Reader.\n\nКниги: {0}\nШрифты: {1}\nСловари: {2}\nЗаметки и резервные копии: {3}\n\nВстроенные шрифты: Lora, IBM Plex Serif (SIL OFL), DejaVu (свободная лицензия). Шаблоны переносов: проект TeX hyph-utf8 (см. assets/hyph).",
         "Lecteur pour Nook Simple Touch (EPUB, FB2, TXT), inspiré de CrossPoint Reader.\n\nLivres : {0}\nPolices : {1}\nDictionnaires : {2}\nNotes et sauvegardes : {3}\n\nPolices incluses : Lora, IBM Plex Serif (SIL OFL), DejaVu (licence libre). Motifs de césure : projet TeX hyph-utf8 (voir assets/hyph).",
         "Lector para Nook Simple Touch (EPUB, FB2, TXT), inspirado en CrossPoint Reader.\n\nLibros: {0}\nFuentes: {1}\nDiccionarios: {2}\nNotas y copias de seguridad: {3}\n\nFuentes incluidas: Lora, IBM Plex Serif (SIL OFL), DejaVu (licencia libre). Patrones de separación silábica: proyecto TeX hyph-utf8 (ver assets/hyph)."},

        // ---- statistics
        {"TODAY", "СЕГОДНЯ", "AUJOURD'HUI", "HOY"},
        {"STREAK", "СЕРИЯ", "SÉRIE", "RACHA"},
        {"FINISHED", "ПРОЧИТАНО", "TERMINÉS", "TERMINADOS"},
        {"GOALS  (tap a goal to change it)", "ЦЕЛИ  (нажмите, чтобы изменить)", "OBJECTIFS  (touchez pour modifier)", "METAS  (toca para cambiar)"},
        {"Daily reading goal", "Дневная цель чтения", "Objectif de lecture quotidien", "Meta diaria de lectura"},
        {"{0} of {1} min today", "{0} из {1} мин сегодня", "{0} sur {1} min aujourd'hui", "{0} de {1} min hoy"},
        {"Goal streak: {0}   \u00B7   reached {1} of last 7 days",
         "Серия целей: {0}   \u00B7   достигнуто {1} из последних 7 дней",
         "Série d'objectifs : {0}   \u00B7   atteint {1} des 7 derniers jours",
         "Racha de metas: {0}   \u00B7   cumplida {1} de los últimos 7 días"},
        {"Books goal {0}", "Цель по книгам на {0}", "Objectif livres {0}", "Meta de libros {0}"},
        {"{0} of {1} books", "{0} из {1} книг", "{0} sur {1} livres", "{0} de {1} libros"},
        {"on track", "по плану", "dans les temps", "en camino"},
        {"{0} behind", "отставание: {0}", "{0} de retard", "{0} de retraso"},
        {"LAST 7 DAYS", "ПОСЛЕДНИЕ 7 ДНЕЙ", "7 DERNIERS JOURS", "ÚLTIMOS 7 DÍAS"},
        {"goal", "цель", "objectif", "meta"},
        {"TOTALS", "ИТОГО", "TOTAUX", "TOTALES"},
        {"Reading time", "Время чтения", "Temps de lecture", "Tiempo de lectura"},
        {"Pages turned", "Страниц перелистано", "Pages tournées", "Páginas pasadas"},
        {"Active days", "Активных дней", "Jours actifs", "Días activos"},
        {"Average per active day", "В среднем за активный день", "Moyenne par jour actif", "Promedio por día activo"},
        {"Reading speed", "Скорость чтения", "Vitesse de lecture", "Velocidad de lectura"},
        {"{0} pages/h", "{0} стр./ч", "{0} pages/h", "{0} págs/h"},
        {"MOST READ", "ЧАЩЕ ВСЕГО ЧИТАЛИСЬ", "LES PLUS LUS", "MÁS LEÍDOS"},
        {"Nothing yet. Open a book and statistics will appear here.",
         "Пока ничего нет. Откройте книгу, и здесь появится статистика.",
         "Rien pour l'instant. Ouvrez un livre et les statistiques apparaîtront ici.",
         "Todavía nada. Abre un libro y aquí aparecerán las estadísticas."},

        // ---- transfer / key test / web page
        {"File transfer\n\nNo Wi-Fi connection.\nTurn on Wi-Fi and connect to your network first.",
         "Передача файлов\n\nНет подключения к Wi-Fi.\nСначала включите Wi-Fi и подключитесь к сети.",
         "Transfert de fichiers\n\nPas de connexion Wi-Fi.\nActivez le Wi-Fi et connectez-vous d'abord à votre réseau.",
         "Transferencia de archivos\n\nSin conexión Wi-Fi.\nActiva el Wi-Fi y conéctate primero a tu red."},
        {"File transfer\n\nOn a phone or computer in the same Wi-Fi network open:\n\n{0}\n\nBooks go to:\n{1}\n\nPress Back to stop.",
         "Передача файлов\n\nНа телефоне или компьютере в той же сети Wi-Fi откройте:\n\n{0}\n\nКниги сохраняются в:\n{1}\n\nНажмите «Назад», чтобы остановить.",
         "Transfert de fichiers\n\nSur un téléphone ou un ordinateur du même réseau Wi-Fi, ouvrez :\n\n{0}\n\nLes livres vont dans :\n{1}\n\nAppuyez sur Retour pour arrêter.",
         "Transferencia de archivos\n\nEn un teléfono u ordenador de la misma red Wi-Fi abre:\n\n{0}\n\nLos libros se guardan en:\n{1}\n\nPulsa Atrás para detener."},
        {"Could not start server:\n{0}", "Не удалось запустить сервер:\n{0}", "Impossible de démarrer le serveur :\n{0}", "No se pudo iniciar el servidor:\n{0}"},
        {"Press any hardware button.\nBack button exits.\nTouch screen to exit.",
         "Нажмите любую аппаратную кнопку.\nКнопка «Назад» — выход.\nКасание экрана — выход.",
         "Appuyez sur un bouton physique.\nLe bouton Retour quitte.\nToucher l'écran quitte.",
         "Pulsa cualquier botón físico.\nEl botón Atrás sale.\nTocar la pantalla sale."},
        {"keyCode = {0}", "код кнопки = {0}", "code de touche = {0}", "código de tecla = {0}"},
        {"Upload", "Загрузить", "Envoyer", "Subir"},
        {"Books", "Книги", "Livres", "Libros"},
        {"Delete", "Удалить", "Supprimer", "Eliminar"},
        {"Fonts (.ttf, .otf) go to the fonts folder, StarDict dictionaries (.ifo, .idx, .dict, .dict.dz, .syn) to the dictionaries folder.",
         "Шрифты (.ttf, .otf) попадают в папку fonts, словари StarDict (.ifo, .idx, .dict, .dict.dz, .syn) — в папку dictionaries.",
         "Les polices (.ttf, .otf) vont dans le dossier fonts, les dictionnaires StarDict (.ifo, .idx, .dict, .dict.dz, .syn) dans le dossier dictionaries.",
         "Las fuentes (.ttf, .otf) van a la carpeta fonts y los diccionarios StarDict (.ifo, .idx, .dict, .dict.dz, .syn) a la carpeta dictionaries."},
    };
}
