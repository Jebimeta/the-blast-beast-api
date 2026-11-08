-- =====================================================================
-- Datos de ejemplo para el perfil dev
-- Se ejecuta solo con spring.profiles.active=dev
-- =====================================================================

insert into users (email, password_hash, display_name, role)
values ('redaccion@theblastbeast.com',
        '$2a$10$JC.3Kq/vYOFP90KzhQVu7Oomzea.LT8a9VxFpoRXmMKf0QF1XltmK',
        'Redacción Blast Beast', 'ADMIN')
on conflict (email) do update
set password_hash = excluded.password_hash,
    display_name = excluded.display_name,
    role = excluded.role;

insert into categories (slug, name, description, sort_order) values
    ('noticias', 'Noticias', 'Lo que pasa en la escena, sin ruido.', 1),
    ('conciertos', 'Conciertos', 'Giras, festivales y fechas confirmadas.', 2),
    ('lanzamientos', 'Lanzamientos', 'Discos, singles y adelantos.', 3),
    ('entrevistas', 'Entrevistas', 'Conversaciones largas con quien hace la música.', 4)
on conflict (slug) do nothing;

insert into bands (slug, name, country, formed_year, genre, website, bandcamp_url, spotify_url) values
    ('metallica', 'Metallica', 'Estados Unidos', 1981, 'Thrash metal', 'https://www.metallica.com', null, null),
    ('judas-priest', 'Judas Priest', 'Reino Unido', 1969, 'Heavy metal', 'https://judaspriest.com', null, null),
    ('opeth', 'Opeth', 'Suecia', 1990, 'Progressive metal', 'https://opeth.com', null, null),
    ('gojira', 'Gojira', 'Francia', 1996, 'Progressive death metal', 'https://gojira-music.com', null, null),
    ('sabaton', 'Sabaton', 'Suecia', 1999, 'Power metal', 'https://sabaton.net', null, null),
    ('iron-maiden', 'Iron Maiden', 'Reino Unido', 1975, 'Heavy metal', 'https://ironmaiden.com', null, null),
    ('nightwish', 'Nightwish', 'Finlandia', 1996, 'Symphonic metal', 'https://nightwish.com', null, null)
on conflict (slug) do nothing;

insert into tags (slug, name) values
    ('giras', 'Giras'),
    ('festivales', 'Festivales'),
    ('nuevo-disco', 'Nuevo disco'),
    ('nuevo-single', 'Nuevo single'),
    ('entrevistas', 'Entrevistas'),
    ('progresivo', 'Progresivo'),
    ('documental', 'Documental'),
    ('noticias', 'Noticias'),
    ('espana', 'España')
on conflict (slug) do nothing;

-- ---------------------------------------------------------------------
-- Artículos publicados
-- ---------------------------------------------------------------------
insert into articles (slug, title, excerpt, body_md, body_html, category_id, author_id, status, published_at, reading_minutes, seo_title, seo_description)
select
    'metallica-anuncia-gira-europea-2027',
    'Metallica anuncia una gira europea de estadios en 2027',
    'La banda confirma doce fechas en estadios, con parada en Madrid y Barcelona. Las entradas salen a la venta el viernes.',
    $md$Metallica ha confirmado esta mañana las primeras doce fechas de su gira europea de 2027. La banda volverá a los estadios después de dos años centrada en festivales y en la promoción de su último trabajo.

## Dos fechas en España

Madrid y Barcelona abrirán la parte española de la gira, con un formato de escenario central que la banda ya probó en Norteamérica. La producción incluye pantallas de 360 grados y un sistema de sonido renovado.

## Entradas

Las entradas saldrán a la venta el viernes a las 10:00. Habrá preventa para miembros del club de fans el miércoles. La organización ha pedido que no se compren entradas en reventa no oficial.$md$,
    $html$<p>Metallica ha confirmado esta mañana las primeras doce fechas de su gira europea de 2027. La banda volverá a los estadios después de dos años centrada en festivales y en la promoción de su último trabajo.</p><h2>Dos fechas en España</h2><p>Madrid y Barcelona abrirán la parte española de la gira, con un formato de escenario central que la banda ya probó en Norteamérica. La producción incluye pantallas de 360 grados y un sistema de sonido renovado.</p><h2>Entradas</h2><p>Las entradas saldrán a la venta el viernes a las 10:00. Habrá preventa para miembros del club de fans el miércoles. La organización ha pedido que no se compren entradas en reventa no oficial.</p>$html$,
    c.id, u.id, 'PUBLISHED', now() - interval '2 days', 4,
    'Metallica anuncia gira europea de estadios en 2027',
    'Doce fechas en estadios, con Madrid y Barcelona incluidas. Entradas a la venta el viernes.'
from categories c, users u
where c.slug = 'conciertos' and u.email = 'redaccion@theblastbeast.com'
on conflict (slug) do nothing;

insert into articles (slug, title, excerpt, body_md, body_html, category_id, author_id, status, published_at, reading_minutes, seo_title, seo_description)
select
    'judas-priest-entra-al-estudio',
    'Judas Priest entra al estudio: nuevo disco en 2027',
    'Rob Halford confirma que la banda ya tiene diez canciones y que el sucesor de Invincible Shield llegará el año que viene.',
    $md$Judas Priest ha empezado a grabar el que será su vigésimo disco de estudio. Rob Halford ha explicado en una entrevista que la banda tiene diez canciones terminadas y que la producción corre a cargo de Andy Sneap.

## Sin prisa, pero sin pausa

El vocalista ha insistido en que no hay fecha cerrada: «Preferimos sacarlo cuando esté bien, no cuando lo pida el calendario». La banda compaginará la grabación con los festivales de verano.

## La gira continúa

Judas Priest mantiene las fechas europeas de 2026 y añadirá nuevas ciudades en 2027. La formación actual sigue siendo la misma que grabó Invincible Shield.$md$,
    $html$<p>Judas Priest ha empezado a grabar el que será su vigésimo disco de estudio. Rob Halford ha explicado en una entrevista que la banda tiene diez canciones terminadas y que la producción corre a cargo de Andy Sneap.</p><h2>Sin prisa, pero sin pausa</h2><p>El vocalista ha insistido en que no hay fecha cerrada: «Preferimos sacarlo cuando esté bien, no cuando lo pida el calendario». La banda compaginará la grabación con los festivales de verano.</p><h2>La gira continúa</h2><p>Judas Priest mantiene las fechas europeas de 2026 y añadirá nuevas ciudades en 2027. La formación actual sigue siendo la misma que grabó Invincible Shield.</p>$html$,
    c.id, u.id, 'PUBLISHED', now() - interval '4 days', 3,
    'Judas Priest prepara su vigésimo disco para 2027',
    'Rob Halford confirma diez canciones terminadas y producción de Andy Sneap.'
from categories c, users u
where c.slug = 'lanzamientos' and u.email = 'redaccion@theblastbeast.com'
on conflict (slug) do nothing;

insert into articles (slug, title, excerpt, body_md, body_html, category_id, author_id, status, published_at, reading_minutes, seo_title, seo_description)
select
    'resurrection-fest-2027-cartel',
    'El Resurrection Fest desvela el cartel de 2027',
    'Gojira, Opeth y Sabaton encabezan una edición que recupera el escenario pequeño para bandas emergentes.',
    $md$El Resurrection Fest ha publicado el cartel completo de su edición de 2027. Gojira, Opeth y Sabaton ocuparán las tres cabezas de cartel, con una cuarta confirmación todavía pendiente.

## Más allá de los grandes nombres

La organización recupera el escenario pequeño que desapareció en 2025. Por ahí pasarán una docena de bandas emergentes de la escena estatal, seleccionadas por convocatoria abierta.

## Entradas y acampada

Los abonos están a la venta desde hoy. La zona de acampada amplía su capacidad y añade duchas gratuitas, una de las peticiones más repetidas en la encuesta de la edición anterior.$md$,
    $html$<p>El Resurrection Fest ha publicado el cartel completo de su edición de 2027. Gojira, Opeth y Sabaton ocuparán las tres cabezas de cartel, con una cuarta confirmación todavía pendiente.</p><h2>Más allá de los grandes nombres</h2><p>La organización recupera el escenario pequeño que desapareció en 2025. Por ahí pasarán una docena de bandas emergentes de la escena estatal, seleccionadas por convocatoria abierta.</p><h2>Entradas y acampada</h2><p>Los abonos están a la venta desde hoy. La zona de acampada amplía su capacidad y añade duchas gratuitas, una de las peticiones más repetidas en la encuesta de la edición anterior.</p>$html$,
    c.id, u.id, 'PUBLISHED', now() - interval '6 days', 5,
    'Resurrection Fest 2027: cartel completo',
    'Gojira, Opeth y Sabaton encabezan el cartel. Vuelve el escenario pequeño para bandas emergentes.'
from categories c, users u
where c.slug = 'conciertos' and u.email = 'redaccion@theblastbeast.com'
on conflict (slug) do nothing;

insert into articles (slug, title, excerpt, body_md, body_html, category_id, author_id, status, published_at, reading_minutes, seo_title, seo_description)
select
    'opeth-entrevista-mikael-aekerfeldt',
    'Mikael Åkerfeldt: «No escribimos para gustar a nadie»',
    'El líder de Opeth habla del nuevo disco, de los años difíciles y de por qué sigue prefiriendo un local pequeño a un estadio.',
    $md$Mikael Åkerfeldt atiende a The Blast Beast antes de la prueba de sonido en Madrid. Habla despacio, elige las palabras y se ríe de sí mismo cuando se pone solemne.

## El disco que no quería escribir

«Durante un tiempo pensé que no volvería a escribir un disco de Opeth», admite. «No por falta de ideas, sino porque no quería repetirme». El nuevo trabajo nace de esas canciones que descartó y que, dos años después, seguían dando vueltas en su cabeza.

## Sobre los estadios

«No escribimos para gustar a nadie. Si mañana solo vienen trescientas personas a un bolo, seguiré tocando igual». La gira española incluye dos salas de aforo medio, una decisión deliberada.

## El futuro

Åkerfeldt no promete nada. «Prefiero deberle un disco a la gente que deberle una explicación».$md$,
    $html$<p>Mikael Åkerfeldt atiende a The Blast Beast antes de la prueba de sonido en Madrid. Habla despacio, elige las palabras y se ríe de sí mismo cuando se pone solemne.</p><h2>El disco que no quería escribir</h2><p>«Durante un tiempo pensé que no volvería a escribir un disco de Opeth», admite. «No por falta de ideas, sino porque no quería repetirme». El nuevo trabajo nace de esas canciones que descartó y que, dos años después, seguían dando vueltas en su cabeza.</p><h2>Sobre los estadios</h2><p>«No escribimos para gustar a nadie. Si mañana solo vienen trescientas personas a un bolo, seguiré tocando igual». La gira española incluye dos salas de aforo medio, una decisión deliberada.</p><h2>El futuro</h2><p>Åkerfeldt no promete nada. «Prefiero deberle un disco a la gente que deberle una explicación».</p>$html$,
    c.id, u.id, 'PUBLISHED', now() - interval '8 days', 7,
    'Entrevista a Mikael Åkerfeldt (Opeth)',
    'El líder de Opeth habla del nuevo disco, de los años difíciles y de tocar en salas pequeñas.'
from categories c, users u
where c.slug = 'entrevistas' and u.email = 'redaccion@theblastbeast.com'
on conflict (slug) do nothing;

insert into articles (slug, title, excerpt, body_md, body_html, category_id, author_id, status, published_at, reading_minutes, seo_title, seo_description)
select
    'gojira-estrena-the-silent-tide',
    'Gojira estrena «The Silent Tide», su primer adelanto',
    'El grupo francés publica el primer single de su próximo disco, un tema más lento y con más peso en la producción.',
    $md$Gojira ha estrenado «The Silent Tide», el primer adelanto de su próximo disco. El tema dura seis minutos y medio y se aleja del sonido más directo de Fortitude.

## Una producción más densa

La mezcla corre a cargo de Josh Wilbur, habitual en la banda desde hace una década. Las guitarras suenan más secas y la batería gana protagonismo en la segunda mitad del tema.

## Gira europea

El grupo ha confirmado una gira europea de salas para el próximo otoño. Las fechas españolas se anunciarán en las próximas semanas.$md$,
    $html$<p>Gojira ha estrenado «The Silent Tide», el primer adelanto de su próximo disco. El tema dura seis minutos y medio y se aleja del sonido más directo de Fortitude.</p><h2>Una producción más densa</h2><p>La mezcla corre a cargo de Josh Wilbur, habitual en la banda desde hace una década. Las guitarras suenan más secas y la batería gana protagonismo en la segunda mitad del tema.</p><h2>Gira europea</h2><p>El grupo ha confirmado una gira europea de salas para el próximo otoño. Las fechas españolas se anunciarán en las próximas semanas.</p>$html$,
    c.id, u.id, 'PUBLISHED', now() - interval '10 days', 3,
    'Gojira estrena «The Silent Tide»',
    'Primer adelanto del nuevo disco de Gojira: más lento, más denso y con Josh Wilbur en la mezcla.'
from categories c, users u
where c.slug = 'lanzamientos' and u.email = 'redaccion@theblastbeast.com'
on conflict (slug) do nothing;

insert into articles (slug, title, excerpt, body_md, body_html, category_id, author_id, status, published_at, reading_minutes, seo_title, seo_description)
select
    'sabaton-tres-fechas-espana',
    'Sabaton anuncia tres fechas en España',
    'La banda sueca volverá en 2027 con un espectáculo centrado en la guerra de invierno. Madrid, Barcelona y Bilbao, confirmadas.',
    $md$Sabaton ha anunciado tres fechas en España dentro de su gira de 2027. La banda sueca presentará un espectáculo nuevo, con un escenario diseñado para explicar la guerra de invierno entre Finlandia y la Unión Soviética.

## Un directo que no es solo música

El grupo lleva años combinando power metal con divulgación histórica. En esta gira, el espectáculo incluye narraciones pregrabadas y una pantalla vertical que cubre todo el fondo del escenario.

## Fechas

Madrid, Barcelona y Bilbao serán las tres paradas españolas. Las entradas salen a la venta la próxima semana.$md$,
    $html$<p>Sabaton ha anunciado tres fechas en España dentro de su gira de 2027. La banda sueca presentará un espectáculo nuevo, con un escenario diseñado para explicar la guerra de invierno entre Finlandia y la Unión Soviética.</p><h2>Un directo que no es solo música</h2><p>El grupo lleva años combinando power metal con divulgación histórica. En esta gira, el espectáculo incluye narraciones pregrabadas y una pantalla vertical que cubre todo el fondo del escenario.</p><h2>Fechas</h2><p>Madrid, Barcelona y Bilbao serán las tres paradas españolas. Las entradas salen a la venta la próxima semana.</p>$html$,
    c.id, u.id, 'PUBLISHED', now() - interval '12 days', 4,
    'Sabaton anuncia tres fechas en España para 2027',
    'Madrid, Barcelona y Bilbao. Un espectáculo centrado en la guerra de invierno.'
from categories c, users u
where c.slug = 'conciertos' and u.email = 'redaccion@theblastbeast.com'
on conflict (slug) do nothing;

insert into articles (slug, title, excerpt, body_md, body_html, category_id, author_id, status, published_at, reading_minutes, seo_title, seo_description)
select
    'iron-maiden-prepara-documental',
    'Iron Maiden prepara un documental sobre sus primeros años',
    'La banda ha restaurado material de archivo de los años setenta y ochenta para una serie documental de tres episodios.',
    $md$Iron Maiden está trabajando en un documental de tres episodios sobre sus primeros años. El proyecto incluye material de archivo restaurado de los primeros conciertos en el East End londinense.

## Material inédito

Según ha podido saber The Blast Beast, el equipo ha recuperado cintas de ensayo de 1978 y grabaciones de la época de Paul Di'Anno. La banda todavía no ha confirmado la plataforma ni la fecha de estreno.

## Sin prisas

El proyecto lleva dos años en producción. La intención es estrenarlo antes de la próxima gira europea, aunque no hay nada cerrado.$md$,
    $html$<p>Iron Maiden está trabajando en un documental de tres episodios sobre sus primeros años. El proyecto incluye material de archivo restaurado de los primeros conciertos en el East End londinense.</p><h2>Material inédito</h2><p>Según ha podido saber The Blast Beast, el equipo ha recuperado cintas de ensayo de 1978 y grabaciones de la época de Paul Di'Anno. La banda todavía no ha confirmado la plataforma ni la fecha de estreno.</p><h2>Sin prisas</h2><p>El proyecto lleva dos años en producción. La intención es estrenarlo antes de la próxima gira europea, aunque no hay nada cerrado.</p>$html$,
    c.id, u.id, 'PUBLISHED', now() - interval '14 days', 3,
    'Iron Maiden prepara un documental sobre sus inicios',
    'Tres episodios con material de archivo restaurado de los años setenta y ochenta.'
from categories c, users u
where c.slug = 'noticias' and u.email = 'redaccion@theblastbeast.com'
on conflict (slug) do nothing;

insert into articles (slug, title, excerpt, body_md, body_html, category_id, author_id, status, published_at, reading_minutes, seo_title, seo_description)
select
    'nightwish-anuncia-paron-indefinido',
    'Nightwish anuncia un parón indefinido',
    'Tuomas Holopainen confirma que la banda se toma un descanso sin fecha de regreso. No habrá gira de despedida.',
    $md$Nightwish ha anunciado un parón indefinido. Tuomas Holopainen ha explicado en un comunicado que la decisión no tiene que ver con conflictos internos, sino con la necesidad de parar después de casi treinta años de actividad continua.

## Sin gira de despedida

La banda ha descartado una gira de despedida. «No queremos convertir esto en un espectáculo triste», señala el comunicado. Las fechas ya anunciadas para 2026 se mantienen.

## Qué pasa con el disco

El último trabajo de estudio sigue en producción y se publicará como estaba previsto. Holopainen trabajará en un proyecto paralelo de música instrumental.$md$,
    $html$<p>Nightwish ha anunciado un parón indefinido. Tuomas Holopainen ha explicado en un comunicado que la decisión no tiene que ver con conflictos internos, sino con la necesidad de parar después de casi treinta años de actividad continua.</p><h2>Sin gira de despedida</h2><p>La banda ha descartado una gira de despedida. «No queremos convertir esto en un espectáculo triste», señala el comunicado. Las fechas ya anunciadas para 2026 se mantienen.</p><h2>Qué pasa con el disco</h2><p>El último trabajo de estudio sigue en producción y se publicará como estaba previsto. Holopainen trabajará en un proyecto paralelo de música instrumental.</p>$html$,
    c.id, u.id, 'PUBLISHED', now() - interval '16 days', 4,
    'Nightwish anuncia un parón indefinido',
    'La banda finlandesa se toma un descanso sin fecha de regreso y sin gira de despedida.'
from categories c, users u
where c.slug = 'noticias' and u.email = 'redaccion@theblastbeast.com'
on conflict (slug) do nothing;

-- ---------------------------------------------------------------------
-- Casos límite para probar 404 y 410
-- ---------------------------------------------------------------------
insert into articles (slug, title, excerpt, body_md, body_html, category_id, author_id, status, published_at, reading_minutes)
select
    'articulo-archivado',
    'Artículo archivado (solo para pruebas)',
    'Este artículo existe pero está archivado. La API debe responder 410.',
    'Contenido de prueba.',
    '<p>Contenido de prueba.</p>',
    c.id, u.id, 'ARCHIVED', now() - interval '30 days', 1
from categories c, users u
where c.slug = 'noticias' and u.email = 'redaccion@theblastbeast.com'
on conflict (slug) do nothing;

insert into articles (slug, title, excerpt, body_md, body_html, category_id, author_id, status, reading_minutes)
select
    'articulo-borrador',
    'Artículo en borrador (solo para pruebas)',
    'Este artículo no está publicado. La API debe responder 404.',
    'Contenido de prueba.',
    '<p>Contenido de prueba.</p>',
    c.id, u.id, 'DRAFT', 1
from categories c, users u
where c.slug = 'noticias' and u.email = 'redaccion@theblastbeast.com'
on conflict (slug) do nothing;

-- ---------------------------------------------------------------------
-- Relaciones
-- ---------------------------------------------------------------------
insert into article_bands (article_id, band_id)
select a.id, b.id
from (values
    ('metallica-anuncia-gira-europea-2027', 'metallica'),
    ('judas-priest-entra-al-estudio', 'judas-priest'),
    ('resurrection-fest-2027-cartel', 'gojira'),
    ('resurrection-fest-2027-cartel', 'opeth'),
    ('resurrection-fest-2027-cartel', 'sabaton'),
    ('opeth-entrevista-mikael-aekerfeldt', 'opeth'),
    ('gojira-estrena-the-silent-tide', 'gojira'),
    ('sabaton-tres-fechas-espana', 'sabaton'),
    ('iron-maiden-prepara-documental', 'iron-maiden'),
    ('nightwish-anuncia-paron-indefinido', 'nightwish')
) as v(article_slug, band_slug)
join articles a on a.slug = v.article_slug
join bands b on b.slug = v.band_slug
on conflict do nothing;

insert into article_tags (article_id, tag_id)
select a.id, t.id
from (values
    ('metallica-anuncia-gira-europea-2027', 'giras'),
    ('metallica-anuncia-gira-europea-2027', 'espana'),
    ('judas-priest-entra-al-estudio', 'nuevo-disco'),
    ('resurrection-fest-2027-cartel', 'festivales'),
    ('resurrection-fest-2027-cartel', 'espana'),
    ('opeth-entrevista-mikael-aekerfeldt', 'entrevistas'),
    ('opeth-entrevista-mikael-aekerfeldt', 'progresivo'),
    ('gojira-estrena-the-silent-tide', 'nuevo-single'),
    ('sabaton-tres-fechas-espana', 'giras'),
    ('sabaton-tres-fechas-espana', 'espana'),
    ('iron-maiden-prepara-documental', 'documental'),
    ('nightwish-anuncia-paron-indefinido', 'noticias')
) as v(article_slug, tag_slug)
join articles a on a.slug = v.article_slug
join tags t on t.slug = v.tag_slug
on conflict do nothing;
