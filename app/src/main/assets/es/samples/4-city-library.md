---
title: La biblioteca municipal
theme: cosmos
---
# Biblioteca municipal
<!-- orbit
id: "library"
-->
> La base de datos inventada de una biblioteca municipal, dibujada como un sistema solar: **124 tablas** en **12 áreas**. Cada planeta es un área; entra en una para ver sus tablas, y luego en una tabla para ver sus columnas.

## 📚 Catálogo
<!-- orbit
id: "area-catalog"
color: "#C2304A"
-->
> 10 tablas. Las más grandes: [`copy_versions`](#copy_versions), [`fee_history`](#fee_history), [`license_logs`](#license_logs).
>
> **Relacionada con** [`author_links`](#author_links) · [`member_counts_2`](#member_counts_2) · [`shelfs`](#shelfs)

### copy_versions
<!-- orbit
id: "copy_versions"
icon: "📇"
collapsed: true
-->
> `copy_versions` · tabla de Catálogo
>
> Una fila por ejemplar, con 18 columnas y unas 454 mil filas.
>
> **Pertenece a** [`staff_settings`](#staff_settings) · [`card_links_2`](#card_links_2) · [`review_counts`](#review_counts)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `copy_versions.id` · `int8` · obligatoria
- 🧩 donation_started
  > `copy_versions.donation_started` · `jsonb` · opcional
- 🧩 event_format
  > `copy_versions.event_format` · `jsonb` · opcional
- 🔢 room_source
  > `copy_versions.room_source` · `int4` · obligatoria
- 🧩 license_state
  > `copy_versions.license_state` · `jsonb` · opcional
- 🕒 notice_active
  > `copy_versions.notice_active` · `timestamp` · opcional
- 🕒 member_archived
  > `copy_versions.member_archived` · `timestamp` · opcional
- 🔤 fee_public
  > `copy_versions.fee_public` · `varchar` · opcional
- ☑️ fee_region
  > `copy_versions.fee_region` · `bool` · opcional
- 🔗 series_id <!-- orbit link: "#series_history", status: "clave foránea" -->
  > `copy_versions.series_id` · `int8` · obligatoria
  >
  > Apunta a [`series_history`](#series_history).
- 🔤 staff_position
  > `copy_versions.staff_position` · `varchar` · opcional
- 🔢 ebook_created
  > `copy_versions.ebook_created` · `int4` · opcional
- 🔗 tag_id <!-- orbit link: "#tag_settings", status: "clave foránea" -->
  > `copy_versions.tag_id` · `int8` · obligatoria
  >
  > Apunta a [`tag_settings`](#tag_settings).
- 🧩 member_external
  > `copy_versions.member_external` · `jsonb` · obligatoria
- ☑️ event_external
  > `copy_versions.event_external` · `bool` · obligatoria
- 🔗 donation_id <!-- orbit link: "#donation_queues", status: "clave foránea" -->
  > `copy_versions.donation_id` · `int8` · obligatoria
  >
  > Apunta a [`donation_queues`](#donation_queues).
- ☑️ card_priority
  > `copy_versions.card_priority` · `bool` · obligatoria
- ☑️ vendor_notes
  > `copy_versions.vendor_notes` · `bool` · opcional

### fee_history
<!-- orbit
id: "fee_history"
icon: "🗃️"
collapsed: true
-->
> `fee_history` · tabla de Catálogo
>
> Una fila por multa, con 9 columnas y unas 203 mil filas.
>
> **Pertenece a** [`report_logs`](#report_logs) · [`shelfs`](#shelfs) · [`device_items`](#device_items)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `fee_history.id` · `int8` · obligatoria
- 🔤 payment_notes
  > `fee_history.payment_notes` · `varchar` · obligatoria
- 🔢 ebook_region
  > `fee_history.ebook_region` · `int4` · opcional
- ☑️ order_total
  > `fee_history.order_total` · `bool` · obligatoria
- 🔤 staff_updated
  > `fee_history.staff_updated` · `varchar` · opcional
- 🔗 hold_id <!-- orbit link: "#hold_links", status: "clave foránea" -->
  > `fee_history.hold_id` · `int8` · obligatoria
  >
  > Apunta a [`hold_links`](#hold_links).
- 🔗 tags_id <!-- orbit link: "#tags", status: "clave foránea" -->
  > `fee_history.tags_id` · `int8` · obligatoria
  >
  > Apunta a [`tags`](#tags).
- 🕒 budget_title
  > `fee_history.budget_title` · `timestamp` · opcional
- 🧩 payment_language
  > `fee_history.payment_language` · `jsonb` · opcional

### license_logs
<!-- orbit
id: "license_logs"
icon: "🧮"
collapsed: true
-->
> `license_logs` · tabla de Catálogo
>
> Una fila por licencia, con 7 columnas y unas 729 mil filas.
>
> **Pertenece a** [`branch_links`](#branch_links)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `license_logs.id` · `int8` · obligatoria
- 🔗 ebook_id <!-- orbit link: "#ebook_versions", status: "clave foránea" -->
  > `license_logs.ebook_id` · `int8` · obligatoria
  >
  > Apunta a [`ebook_versions`](#ebook_versions).
- 🕒 ebook_count
  > `license_logs.ebook_count` · `timestamp` · opcional
- 🕒 ebook_reviewed
  > `license_logs.ebook_reviewed` · `timestamp` · obligatoria
- 🔤 budget_external
  > `license_logs.budget_external` · `varchar` · obligatoria
- 🔢 role_external
  > `license_logs.role_external` · `int4` · obligatoria
- 🔗 hold_id <!-- orbit link: "#hold_logs", status: "clave foránea" -->
  > `license_logs.hold_id` · `int8` · obligatoria
  >
  > Apunta a [`hold_logs`](#hold_logs).

### vendor_items
<!-- orbit
id: "vendor_items"
icon: "🗃️"
collapsed: true
-->
> `vendor_items` · tabla de Catálogo
>
> Una fila por proveedor, con 11 columnas y unas 336 mil filas.
>
> **Pertenece a** [`budget_settings_2`](#budget_settings_2) · [`vendor_counts`](#vendor_counts) · [`card_logs`](#card_logs) · [`fee_requests`](#fee_requests)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `vendor_items.id` · `int8` · obligatoria
- 🔗 author_id <!-- orbit link: "#author_history", status: "clave foránea" -->
  > `vendor_items.author_id` · `int8` · obligatoria
  >
  > Apunta a [`author_history`](#author_history).
- 🔤 card_state
  > `vendor_items.card_state` · `varchar` · opcional
- 🔤 notice_region
  > `vendor_items.notice_region` · `varchar` · opcional
- 🔗 loan_id <!-- orbit link: "#loan_history", status: "clave foránea" -->
  > `vendor_items.loan_id` · `int8` · obligatoria
  >
  > Apunta a [`loan_history`](#loan_history).
- 🧩 license_language
  > `vendor_items.license_language` · `jsonb` · obligatoria
- 🕒 donation_external
  > `vendor_items.donation_external` · `timestamp` · obligatoria
- 🔗 review_id <!-- orbit link: "#review_versions", status: "clave foránea" -->
  > `vendor_items.review_id` · `int8` · obligatoria
  >
  > Apunta a [`review_versions`](#review_versions).
- 🔗 license_id <!-- orbit link: "#license_logs", status: "clave foránea" -->
  > `vendor_items.license_id` · `int8` · obligatoria
  >
  > Apunta a [`license_logs`](#license_logs).
- 🕒 member_kind
  > `vendor_items.member_kind` · `timestamp` · obligatoria
- 🔤 budget_active
  > `vendor_items.budget_active` · `varchar` · obligatoria

### report_logs
<!-- orbit
id: "report_logs"
icon: "🗃️"
collapsed: true
-->
> `report_logs` · tabla de Catálogo
>
> Una fila por informe, con 24 columnas y unas 452 mil filas.
>
> **Pertenece a** [`hold_links`](#hold_links) · [`visits`](#visits)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `report_logs.id` · `int8` · obligatoria
- 🧩 review_name
  > `report_logs.review_name` · `jsonb` · opcional
- 🧩 repair_status
  > `report_logs.repair_status` · `jsonb` · obligatoria
- 🔤 vendor_name
  > `report_logs.vendor_name` · `varchar` · obligatoria
- 🧩 loan_code
  > `report_logs.loan_code` · `jsonb` · opcional
- 🧩 payment_language
  > `report_logs.payment_language` · `jsonb` · opcional
- 🔢 book_status
  > `report_logs.book_status` · `int4` · obligatoria
- 🧩 tag_position
  > `report_logs.tag_position` · `jsonb` · opcional
- 🕒 repair_count
  > `report_logs.repair_count` · `timestamp` · obligatoria
- 🕒 loan_code_2
  > `report_logs.loan_code_2` · `timestamp` · obligatoria
- ☑️ book_label
  > `report_logs.book_label` · `bool` · opcional
- ☑️ repair_language
  > `report_logs.repair_language` · `bool` · opcional
- 🔢 tag_amount
  > `report_logs.tag_amount` · `int4` · opcional
- 🔢 member_external
  > `report_logs.member_external` · `int4` · obligatoria
- 🔗 review_id <!-- orbit link: "#review_items", status: "clave foránea" -->
  > `report_logs.review_id` · `int8` · obligatoria
  >
  > Apunta a [`review_items`](#review_items).
- 🔤 review_status
  > `report_logs.review_status` · `varchar` · opcional
- 🔢 report_title
  > `report_logs.report_title` · `int4` · obligatoria
- 🔗 holds_id <!-- orbit link: "#holds_2", status: "clave foránea" -->
  > `report_logs.holds_id` · `int8` · obligatoria
  >
  > Apunta a [`holds_2`](#holds_2).
- 🔤 author_reviewed
  > `report_logs.author_reviewed` · `varchar` · opcional
- 🔤 order_count
  > `report_logs.order_count` · `varchar` · obligatoria
- 🧩 series_public
  > `report_logs.series_public` · `jsonb` · obligatoria
- ☑️ visit_language
  > `report_logs.visit_language` · `bool` · obligatoria
- 🕒 staff_reviewed
  > `report_logs.staff_reviewed` · `timestamp` · opcional
- 🕒 role_total
  > `report_logs.role_total` · `timestamp` · obligatoria

### visit_items
<!-- orbit
id: "visit_items"
icon: "📇"
collapsed: true
-->
> `visit_items` · tabla de Catálogo
>
> Una fila por visita, con 7 columnas y unas 234 mil filas.
>
> **Pertenece a** [`donations`](#donations) · [`holds`](#holds) · [`visits`](#visits) · [`shelf_counts`](#shelf_counts)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `visit_items.id` · `int8` · obligatoria
- 🔢 fee_region
  > `visit_items.fee_region` · `int4` · opcional
- ☑️ device_total
  > `visit_items.device_total` · `bool` · opcional
- 🔗 notice_id <!-- orbit link: "#notice_counts", status: "clave foránea" -->
  > `visit_items.notice_id` · `int8` · obligatoria
  >
  > Apunta a [`notice_counts`](#notice_counts).
- 🕒 event_ended
  > `visit_items.event_ended` · `timestamp` · opcional
- 🔤 license_status
  > `visit_items.license_status` · `varchar` · opcional
- ☑️ room_created
  > `visit_items.room_created` · `bool` · opcional

### author_links
<!-- orbit
id: "author_links"
icon: "🧮"
collapsed: true
-->
> `author_links` · tabla de Catálogo
>
> Una fila por autor, con 19 columnas y unas 399 mil filas.
>
> **Pertenece a** [`report_queues_2`](#report_queues_2) · [`staff_counts`](#staff_counts) · [`audit_queues`](#audit_queues)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `author_links.id` · `int8` · obligatoria
- 🔗 staff_id <!-- orbit link: "#staff_counts", status: "clave foránea" -->
  > `author_links.staff_id` · `int8` · obligatoria
  >
  > Apunta a [`staff_counts`](#staff_counts).
- ☑️ license_state
  > `author_links.license_state` · `bool` · obligatoria
- 🕒 budget_code
  > `author_links.budget_code` · `timestamp` · opcional
- 🔗 series_id <!-- orbit link: "#series_links", status: "clave foránea" -->
  > `author_links.series_id` · `int8` · obligatoria
  >
  > Apunta a [`series_links`](#series_links).
- 🔤 tag_total
  > `author_links.tag_total` · `varchar` · opcional
- 🔢 staff_state
  > `author_links.staff_state` · `int4` · obligatoria
- ☑️ report_format
  > `author_links.report_format` · `bool` · obligatoria
- 🔗 audit_id <!-- orbit link: "#audit_queues", status: "clave foránea" -->
  > `author_links.audit_id` · `int8` · obligatoria
  >
  > Apunta a [`audit_queues`](#audit_queues).
- 🔗 copy_id <!-- orbit link: "#copy_items", status: "clave foránea" -->
  > `author_links.copy_id` · `int8` · obligatoria
  >
  > Apunta a [`copy_items`](#copy_items).
- 🔢 book_created
  > `author_links.book_created` · `int4` · opcional
- ☑️ audit_name
  > `author_links.audit_name` · `bool` · opcional
- 🔤 visit_public
  > `author_links.visit_public` · `varchar` · obligatoria
- 🔗 payment_id <!-- orbit link: "#payment_settings", status: "clave foránea" -->
  > `author_links.payment_id` · `int8` · obligatoria
  >
  > Apunta a [`payment_settings`](#payment_settings).
- 🕒 license_archived
  > `author_links.license_archived` · `timestamp` · obligatoria
- 🧩 notice_amount
  > `author_links.notice_amount` · `jsonb` · opcional
- 🕒 notice_title
  > `author_links.notice_title` · `timestamp` · opcional
- 🧩 donation_source
  > `author_links.donation_source` · `jsonb` · opcional
- 🧩 payment_position
  > `author_links.payment_position` · `jsonb` · obligatoria

### donation_links
<!-- orbit
id: "donation_links"
icon: "🧮"
collapsed: true
-->
> `donation_links` · tabla de Catálogo
>
> Una fila por donación, con 17 columnas y unas 146 mil filas.
>
> **Pertenece a** [`report_logs_2`](#report_logs_2) · [`payment_versions`](#payment_versions) · [`book_settings`](#book_settings)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `donation_links.id` · `int8` · obligatoria
- ☑️ member_total
  > `donation_links.member_total` · `bool` · opcional
- ☑️ audit_archived
  > `donation_links.audit_archived` · `bool` · opcional
- 🕒 payment_archived
  > `donation_links.payment_archived` · `timestamp` · opcional
- 🔢 loan_source
  > `donation_links.loan_source` · `int4` · opcional
- ☑️ repair_region
  > `donation_links.repair_region` · `bool` · obligatoria
- 🔤 payment_created
  > `donation_links.payment_created` · `varchar` · obligatoria
- 🔢 role_notes
  > `donation_links.role_notes` · `int4` · obligatoria
- ☑️ member_ended
  > `donation_links.member_ended` · `bool` · opcional
- 🔗 notice_id <!-- orbit link: "#notice_counts", status: "clave foránea" -->
  > `donation_links.notice_id` · `int8` · obligatoria
  >
  > Apunta a [`notice_counts`](#notice_counts).
- ☑️ device_label
  > `donation_links.device_label` · `bool` · obligatoria
- 🕒 member_total_2
  > `donation_links.member_total_2` · `timestamp` · obligatoria
- 🕒 visit_started
  > `donation_links.visit_started` · `timestamp` · opcional
- 🔤 report_archived
  > `donation_links.report_archived` · `varchar` · obligatoria
- 🧩 staff_label
  > `donation_links.staff_label` · `jsonb` · opcional
- 🧩 donation_priority
  > `donation_links.donation_priority` · `jsonb` · opcional
- 🔗 tag_id <!-- orbit link: "#tag_requests", status: "clave foránea" -->
  > `donation_links.tag_id` · `int8` · obligatoria
  >
  > Apunta a [`tag_requests`](#tag_requests).

### budget_settings
<!-- orbit
id: "budget_settings"
icon: "📇"
collapsed: true
-->
> `budget_settings` · tabla de Catálogo
>
> Una fila por presupuesto, con 23 columnas y unas 153 mil filas.
>
> **Pertenece a** [`review_items`](#review_items) · [`visit_items`](#visit_items) · [`loan_logs_2`](#loan_logs_2) · [`card_queues`](#card_queues)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `budget_settings.id` · `int8` · obligatoria
- ☑️ card_language
  > `budget_settings.card_language` · `bool` · obligatoria
- ☑️ review_reviewed
  > `budget_settings.review_reviewed` · `bool` · opcional
- 🔤 fee_external
  > `budget_settings.fee_external` · `varchar` · opcional
- 🔤 event_reviewed
  > `budget_settings.event_reviewed` · `varchar` · obligatoria
- ☑️ donation_state
  > `budget_settings.donation_state` · `bool` · opcional
- 🔤 device_label
  > `budget_settings.device_label` · `varchar` · obligatoria
- 🔗 shelf_id <!-- orbit link: "#shelf_queues", status: "clave foránea" -->
  > `budget_settings.shelf_id` · `int8` · obligatoria
  >
  > Apunta a [`shelf_queues`](#shelf_queues).
- 🕒 vendor_started
  > `budget_settings.vendor_started` · `timestamp` · obligatoria
- 🕒 review_amount
  > `budget_settings.review_amount` · `timestamp` · obligatoria
- 🔢 vendor_active
  > `budget_settings.vendor_active` · `int4` · obligatoria
- ☑️ budget_started
  > `budget_settings.budget_started` · `bool` · opcional
- 🧩 role_format
  > `budget_settings.role_format` · `jsonb` · opcional
- 🔗 license_id <!-- orbit link: "#license_logs", status: "clave foránea" -->
  > `budget_settings.license_id` · `int8` · obligatoria
  >
  > Apunta a [`license_logs`](#license_logs).
- 🧩 notice_source
  > `budget_settings.notice_source` · `jsonb` · opcional
- 🔗 budget_id <!-- orbit link: "#budget_settings_2", status: "clave foránea" -->
  > `budget_settings.budget_id` · `int8` · obligatoria
  >
  > Apunta a [`budget_settings_2`](#budget_settings_2).
- 🧩 budget_code
  > `budget_settings.budget_code` · `jsonb` · obligatoria
- 🔢 device_started
  > `budget_settings.device_started` · `int4` · opcional
- 🔢 vendor_updated
  > `budget_settings.vendor_updated` · `int4` · opcional
- 🕒 device_started_2
  > `budget_settings.device_started_2` · `timestamp` · obligatoria
- 🔤 audit_started
  > `budget_settings.audit_started` · `varchar` · obligatoria
- 🧩 audit_source
  > `budget_settings.audit_source` · `jsonb` · obligatoria
- 🔗 shelf_id_2 <!-- orbit link: "#shelf_counts", status: "clave foránea" -->
  > `budget_settings.shelf_id_2` · `int8` · obligatoria
  >
  > Apunta a [`shelf_counts`](#shelf_counts).

### copy_items
<!-- orbit
id: "copy_items"
icon: "🧮"
collapsed: true
-->
> `copy_items` · tabla de Catálogo
>
> Una fila por ejemplar, con 11 columnas y unas 367 mil filas.
>
> **Pertenece a** [`review_queues`](#review_queues)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `copy_items.id` · `int8` · obligatoria
- 🧩 payment_reviewed
  > `copy_items.payment_reviewed` · `jsonb` · opcional
- 🔗 payment_id <!-- orbit link: "#payment_versions", status: "clave foránea" -->
  > `copy_items.payment_id` · `int8` · obligatoria
  >
  > Apunta a [`payment_versions`](#payment_versions).
- 🔤 copy_format
  > `copy_items.copy_format` · `varchar` · obligatoria
- 🕒 hold_updated
  > `copy_items.hold_updated` · `timestamp` · obligatoria
- 🧩 fee_kind
  > `copy_items.fee_kind` · `jsonb` · obligatoria
- ☑️ shelf_kind
  > `copy_items.shelf_kind` · `bool` · obligatoria
- 🔗 card_id <!-- orbit link: "#card_links_2", status: "clave foránea" -->
  > `copy_items.card_id` · `int8` · obligatoria
  >
  > Apunta a [`card_links_2`](#card_links_2).
- 🧩 event_language
  > `copy_items.event_language` · `jsonb` · opcional
- 🕒 shelf_updated
  > `copy_items.shelf_updated` · `timestamp` · obligatoria
- 🔗 order_id <!-- orbit link: "#order_items", status: "clave foránea" -->
  > `copy_items.order_id` · `int8` · obligatoria
  >
  > Apunta a [`order_items`](#order_items).

## 🧑‍🤝‍🧑 Socios
<!-- orbit
id: "area-members"
color: "#2F7CF6"
-->
> 9 tablas. Las más grandes: [`member_counts`](#member_counts), [`order_versions`](#order_versions), [`series_requests`](#series_requests).
>
> **Relacionada con** [`holds`](#holds) · [`audit_settings`](#audit_settings) · [`room_requests`](#room_requests)

### member_counts
<!-- orbit
id: "member_counts"
icon: "📇"
collapsed: true
-->
> `member_counts` · tabla de Socios
>
> Una fila por socio, con 80 columnas y unas 175 mil filas.
>
> **Pertenece a** [`ebook_requests`](#ebook_requests)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `member_counts.id` · `int8` · obligatoria
- ☑️ series_active
  > `member_counts.series_active` · `bool` · opcional
- 🧩 branch_amount
  > `member_counts.branch_amount` · `jsonb` · obligatoria
- 🔤 visit_updated
  > `member_counts.visit_updated` · `varchar` · opcional
- ☑️ book_priority
  > `member_counts.book_priority` · `bool` · opcional
- 🔤 role_archived
  > `member_counts.role_archived` · `varchar` · obligatoria
- 🔤 hold_position
  > `member_counts.hold_position` · `varchar` · opcional
- 🧩 branch_ended
  > `member_counts.branch_ended` · `jsonb` · opcional
- 🔢 event_state
  > `member_counts.event_state` · `int4` · opcional
- ☑️ donation_active
  > `member_counts.donation_active` · `bool` · opcional
- ☑️ card_active
  > `member_counts.card_active` · `bool` · opcional
- 🧩 repair_public
  > `member_counts.repair_public` · `jsonb` · opcional
- 🔗 payment_id <!-- orbit link: "#payment_settings", status: "clave foránea" -->
  > `member_counts.payment_id` · `int8` · obligatoria
  >
  > Apunta a [`payment_settings`](#payment_settings).
- ☑️ order_public
  > `member_counts.order_public` · `bool` · obligatoria
- 🔤 event_label
  > `member_counts.event_label` · `varchar` · opcional
- 🕒 ebook_reviewed
  > `member_counts.ebook_reviewed` · `timestamp` · obligatoria
- 🔢 book_label
  > `member_counts.book_label` · `int4` · obligatoria
- 🔗 budget_id <!-- orbit link: "#budget_queues", status: "clave foránea" -->
  > `member_counts.budget_id` · `int8` · obligatoria
  >
  > Apunta a [`budget_queues`](#budget_queues).
- ☑️ branch_code
  > `member_counts.branch_code` · `bool` · opcional
- ☑️ report_label
  > `member_counts.report_label` · `bool` · obligatoria
- 🔤 repair_state
  > `member_counts.repair_state` · `varchar` · obligatoria
- 🔗 member_id <!-- orbit link: "#member_counts_2", status: "clave foránea" -->
  > `member_counts.member_id` · `int8` · obligatoria
  >
  > Apunta a [`member_counts_2`](#member_counts_2).
- 🔢 tag_language
  > `member_counts.tag_language` · `int4` · opcional
- ☑️ staff_count
  > `member_counts.staff_count` · `bool` · opcional
- 🔤 fee_created
  > `member_counts.fee_created` · `varchar` · obligatoria
- 🕒 role_updated
  > `member_counts.role_updated` · `timestamp` · obligatoria
- 🔗 tag_id <!-- orbit link: "#tag_counts", status: "clave foránea" -->
  > `member_counts.tag_id` · `int8` · obligatoria
  >
  > Apunta a [`tag_counts`](#tag_counts).
- 🔢 loan_external
  > `member_counts.loan_external` · `int4` · opcional
- 🔤 copy_title
  > `member_counts.copy_title` · `varchar` · obligatoria
- 🔗 order_id <!-- orbit link: "#order_logs", status: "clave foránea" -->
  > `member_counts.order_id` · `int8` · obligatoria
  >
  > Apunta a [`order_logs`](#order_logs).
- 🕒 donation_code
  > `member_counts.donation_code` · `timestamp` · obligatoria
- 🔢 copy_archived
  > `member_counts.copy_archived` · `int4` · obligatoria
- 🔢 device_notes
  > `member_counts.device_notes` · `int4` · obligatoria
- 🔢 shelf_public
  > `member_counts.shelf_public` · `int4` · obligatoria
- 🕒 notice_amount
  > `member_counts.notice_amount` · `timestamp` · obligatoria
- 🔤 license_position
  > `member_counts.license_position` · `varchar` · obligatoria
- 🕒 ebook_notes
  > `member_counts.ebook_notes` · `timestamp` · opcional
- 🕒 branch_reviewed
  > `member_counts.branch_reviewed` · `timestamp` · obligatoria
- ☑️ budget_active
  > `member_counts.budget_active` · `bool` · obligatoria
- ☑️ series_active_2
  > `member_counts.series_active_2` · `bool` · opcional
- 🔢 notice_name
  > `member_counts.notice_name` · `int4` · obligatoria
- 🔢 hold_started
  > `member_counts.hold_started` · `int4` · obligatoria
- 🔗 report_id <!-- orbit link: "#report_counts", status: "clave foránea" -->
  > `member_counts.report_id` · `int8` · obligatoria
  >
  > Apunta a [`report_counts`](#report_counts).
- 🧩 hold_public
  > `member_counts.hold_public` · `jsonb` · opcional
- 🔗 card_id <!-- orbit link: "#card_queues", status: "clave foránea" -->
  > `member_counts.card_id` · `int8` · obligatoria
  >
  > Apunta a [`card_queues`](#card_queues).
- ☑️ series_format
  > `member_counts.series_format` · `bool` · opcional
- 🔗 staff_id <!-- orbit link: "#staff_logs", status: "clave foránea" -->
  > `member_counts.staff_id` · `int8` · obligatoria
  >
  > Apunta a [`staff_logs`](#staff_logs).
- 🔢 author_public
  > `member_counts.author_public` · `int4` · obligatoria
- 🔢 budget_status
  > `member_counts.budget_status` · `int4` · obligatoria
- ☑️ room_source
  > `member_counts.room_source` · `bool` · opcional
- 🔢 shelf_language
  > `member_counts.shelf_language` · `int4` · obligatoria
- 🔢 shelf_kind
  > `member_counts.shelf_kind` · `int4` · opcional
- 🔤 donation_format
  > `member_counts.donation_format` · `varchar` · opcional
- 🔢 shelf_updated
  > `member_counts.shelf_updated` · `int4` · obligatoria
- ☑️ audit_title
  > `member_counts.audit_title` · `bool` · opcional
- 🧩 member_ended
  > `member_counts.member_ended` · `jsonb` · obligatoria
- 🔗 vendor_id <!-- orbit link: "#vendor_history", status: "clave foránea" -->
  > `member_counts.vendor_id` · `int8` · obligatoria
  >
  > Apunta a [`vendor_history`](#vendor_history).
- 🕒 fee_reviewed
  > `member_counts.fee_reviewed` · `timestamp` · opcional
- 🔢 member_active
  > `member_counts.member_active` · `int4` · obligatoria
- 🧩 budget_source
  > `member_counts.budget_source` · `jsonb` · opcional
- ☑️ license_priority
  > `member_counts.license_priority` · `bool` · obligatoria
- 🔗 payment_id_2 <!-- orbit link: "#payment_requests", status: "clave foránea" -->
  > `member_counts.payment_id_2` · `int8` · obligatoria
  >
  > Apunta a [`payment_requests`](#payment_requests).
- 🕒 event_priority
  > `member_counts.event_priority` · `timestamp` · obligatoria
- 🔗 author_id <!-- orbit link: "#author_links", status: "clave foránea" -->
  > `member_counts.author_id` · `int8` · obligatoria
  >
  > Apunta a [`author_links`](#author_links).
- 🔗 copy_id <!-- orbit link: "#copy_counts", status: "clave foránea" -->
  > `member_counts.copy_id` · `int8` · obligatoria
  >
  > Apunta a [`copy_counts`](#copy_counts).
- 🔤 staff_code
  > `member_counts.staff_code` · `varchar` · opcional
- 🕒 card_code
  > `member_counts.card_code` · `timestamp` · opcional
- 🔢 role_notes
  > `member_counts.role_notes` · `int4` · opcional
- 🧩 member_public
  > `member_counts.member_public` · `jsonb` · opcional
- 🔗 loan_id <!-- orbit link: "#loan_history", status: "clave foránea" -->
  > `member_counts.loan_id` · `int8` · obligatoria
  >
  > Apunta a [`loan_history`](#loan_history).
- 🕒 payment_status
  > `member_counts.payment_status` · `timestamp` · obligatoria
- 🔤 branch_started
  > `member_counts.branch_started` · `varchar` · obligatoria
- ☑️ staff_state
  > `member_counts.staff_state` · `bool` · opcional
- ☑️ visit_label
  > `member_counts.visit_label` · `bool` · opcional
- 🧩 loan_created
  > `member_counts.loan_created` · `jsonb` · obligatoria
- 🧩 room_status
  > `member_counts.room_status` · `jsonb` · obligatoria
- 🕒 role_language
  > `member_counts.role_language` · `timestamp` · obligatoria
- 🔗 fee_id <!-- orbit link: "#fee_requests", status: "clave foránea" -->
  > `member_counts.fee_id` · `int8` · obligatoria
  >
  > Apunta a [`fee_requests`](#fee_requests).
- 🕒 visit_count
  > `member_counts.visit_count` · `timestamp` · opcional
- ☑️ loan_state
  > `member_counts.loan_state` · `bool` · obligatoria

### order_versions
<!-- orbit
id: "order_versions"
icon: "🧷"
collapsed: true
-->
> `order_versions` · tabla de Socios
>
> Una fila por pedido, con 18 columnas y unas 804 mil filas.
>
> **Pertenece a** [`device_items`](#device_items) · [`audit_queues`](#audit_queues) · [`report_items`](#report_items)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `order_versions.id` · `int8` · obligatoria
- 🕒 shelf_status
  > `order_versions.shelf_status` · `timestamp` · opcional
- 🔢 member_created
  > `order_versions.member_created` · `int4` · opcional
- 🔤 series_public
  > `order_versions.series_public` · `varchar` · obligatoria
- 🧩 member_kind
  > `order_versions.member_kind` · `jsonb` · obligatoria
- 🔢 copy_reviewed
  > `order_versions.copy_reviewed` · `int4` · obligatoria
- 🔗 series_id <!-- orbit link: "#series_counts", status: "clave foránea" -->
  > `order_versions.series_id` · `int8` · obligatoria
  >
  > Apunta a [`series_counts`](#series_counts).
- 🔢 role_started
  > `order_versions.role_started` · `int4` · opcional
- 🔤 copy_external
  > `order_versions.copy_external` · `varchar` · obligatoria
- 🔗 donation_id <!-- orbit link: "#donation_history", status: "clave foránea" -->
  > `order_versions.donation_id` · `int8` · obligatoria
  >
  > Apunta a [`donation_history`](#donation_history).
- ☑️ hold_created
  > `order_versions.hold_created` · `bool` · obligatoria
- 🧩 notice_created
  > `order_versions.notice_created` · `jsonb` · opcional
- 🔤 device_code
  > `order_versions.device_code` · `varchar` · obligatoria
- 🔢 license_updated
  > `order_versions.license_updated` · `int4` · obligatoria
- 🔗 loan_id <!-- orbit link: "#loan_logs", status: "clave foránea" -->
  > `order_versions.loan_id` · `int8` · obligatoria
  >
  > Apunta a [`loan_logs`](#loan_logs).
- 🔗 staff_id <!-- orbit link: "#staff_counts", status: "clave foránea" -->
  > `order_versions.staff_id` · `int8` · obligatoria
  >
  > Apunta a [`staff_counts`](#staff_counts).
- 🕒 audit_source
  > `order_versions.audit_source` · `timestamp` · opcional
- 🕒 notice_created_2
  > `order_versions.notice_created_2` · `timestamp` · opcional

### series_requests
<!-- orbit
id: "series_requests"
icon: "🗂️"
collapsed: true
-->
> `series_requests` · tabla de Socios
>
> Una fila por serie, con 6 columnas y unas 472 mil filas.
>
> **Pertenece a** [`device_settings`](#device_settings) · [`ebook_logs`](#ebook_logs)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `series_requests.id` · `int8` · obligatoria
- 🔢 book_started
  > `series_requests.book_started` · `int4` · obligatoria
- 🔢 report_updated
  > `series_requests.report_updated` · `int4` · opcional
- 🔤 ebook_count
  > `series_requests.ebook_count` · `varchar` · obligatoria
- 🔢 visit_code
  > `series_requests.visit_code` · `int4` · obligatoria
- 🔗 visit_id <!-- orbit link: "#visit_items", status: "clave foránea" -->
  > `series_requests.visit_id` · `int8` · obligatoria
  >
  > Apunta a [`visit_items`](#visit_items).

### donation_requests
<!-- orbit
id: "donation_requests"
icon: "🧷"
collapsed: true
-->
> `donation_requests` · tabla de Socios
>
> Una fila por donación, con 7 columnas y unas 764 mil filas.
>
> **Pertenece a** [`budgets`](#budgets) · [`review_items`](#review_items)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `donation_requests.id` · `int8` · obligatoria
- 🧩 hold_state
  > `donation_requests.hold_state` · `jsonb` · opcional
- 🔗 report_id <!-- orbit link: "#report_counts", status: "clave foránea" -->
  > `donation_requests.report_id` · `int8` · obligatoria
  >
  > Apunta a [`report_counts`](#report_counts).
- 🧩 loan_updated
  > `donation_requests.loan_updated` · `jsonb` · obligatoria
- 🕒 ebook_position
  > `donation_requests.ebook_position` · `timestamp` · opcional
- 🔢 device_started
  > `donation_requests.device_started` · `int4` · obligatoria
- ☑️ budget_amount
  > `donation_requests.budget_amount` · `bool` · obligatoria

### events
<!-- orbit
id: "events"
icon: "📇"
collapsed: true
-->
> `events` · tabla de Socios
>
> Una fila por evento, con 18 columnas y unas 464 mil filas.
>
> **Pertenece a** [`card_queues`](#card_queues) · [`report_versions`](#report_versions) · [`order_links`](#order_links)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `events.id` · `int8` · obligatoria
- 🔢 report_code
  > `events.report_code` · `int4` · obligatoria
- 🕒 ebook_updated
  > `events.ebook_updated` · `timestamp` · obligatoria
- 🕒 fee_external
  > `events.fee_external` · `timestamp` · opcional
- 🔢 device_notes
  > `events.device_notes` · `int4` · opcional
- 🧩 visit_total
  > `events.visit_total` · `jsonb` · obligatoria
- 🔗 book_id <!-- orbit link: "#book_links", status: "clave foránea" -->
  > `events.book_id` · `int8` · obligatoria
  >
  > Apunta a [`book_links`](#book_links).
- 🔢 hold_total
  > `events.hold_total` · `int4` · obligatoria
- 🔢 card_started
  > `events.card_started` · `int4` · opcional
- 🔤 order_position
  > `events.order_position` · `varchar` · obligatoria
- 🕒 shelf_archived
  > `events.shelf_archived` · `timestamp` · opcional
- 🔢 shelf_priority
  > `events.shelf_priority` · `int4` · obligatoria
- 🧩 loan_ended
  > `events.loan_ended` · `jsonb` · obligatoria
- 🔗 ebook_id <!-- orbit link: "#ebook_versions", status: "clave foránea" -->
  > `events.ebook_id` · `int8` · obligatoria
  >
  > Apunta a [`ebook_versions`](#ebook_versions).
- ☑️ fee_archived
  > `events.fee_archived` · `bool` · opcional
- 🔤 license_total
  > `events.license_total` · `varchar` · opcional
- 🧩 notice_active
  > `events.notice_active` · `jsonb` · opcional
- 🧩 author_total
  > `events.author_total` · `jsonb` · obligatoria

### report_queues
<!-- orbit
id: "report_queues"
icon: "🗂️"
collapsed: true
-->
> `report_queues` · tabla de Socios
>
> Una fila por informe, con 17 columnas y unas 584 mil filas.
>
> **Pertenece a** [`holds_2`](#holds_2) · [`loan_counts`](#loan_counts)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `report_queues.id` · `int8` · obligatoria
- 🔢 repair_notes
  > `report_queues.repair_notes` · `int4` · obligatoria
- 🧩 notice_reviewed
  > `report_queues.notice_reviewed` · `jsonb` · opcional
- 🔗 holds_id <!-- orbit link: "#holds", status: "clave foránea" -->
  > `report_queues.holds_id` · `int8` · obligatoria
  >
  > Apunta a [`holds`](#holds).
- 🧩 report_language
  > `report_queues.report_language` · `jsonb` · obligatoria
- 🔗 author_id <!-- orbit link: "#author_links_3", status: "clave foránea" -->
  > `report_queues.author_id` · `int8` · obligatoria
  >
  > Apunta a [`author_links_3`](#author_links_3).
- 🧩 notice_code
  > `report_queues.notice_code` · `jsonb` · obligatoria
- 🕒 report_status
  > `report_queues.report_status` · `timestamp` · opcional
- 🔢 member_status
  > `report_queues.member_status` · `int4` · obligatoria
- 🔢 tag_name
  > `report_queues.tag_name` · `int4` · opcional
- 🔢 copy_public
  > `report_queues.copy_public` · `int4` · opcional
- 🔢 copy_reviewed
  > `report_queues.copy_reviewed` · `int4` · opcional
- ☑️ audit_name
  > `report_queues.audit_name` · `bool` · opcional
- ☑️ hold_label
  > `report_queues.hold_label` · `bool` · obligatoria
- 🕒 card_priority
  > `report_queues.card_priority` · `timestamp` · opcional
- 🕒 copy_created
  > `report_queues.copy_created` · `timestamp` · opcional
- 🕒 room_name
  > `report_queues.room_name` · `timestamp` · opcional

### card_links
<!-- orbit
id: "card_links"
icon: "🗂️"
collapsed: true
-->
> `card_links` · tabla de Socios
>
> Una fila por credencial, con 17 columnas y unas 262 mil filas.
>
> **Pertenece a** [`notice_requests`](#notice_requests) · [`loan_logs`](#loan_logs)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `card_links.id` · `int8` · obligatoria
- 🕒 branch_name
  > `card_links.branch_name` · `timestamp` · obligatoria
- 🔤 ebook_label
  > `card_links.ebook_label` · `varchar` · obligatoria
- 🔗 license_id <!-- orbit link: "#license_queues", status: "clave foránea" -->
  > `card_links.license_id` · `int8` · obligatoria
  >
  > Apunta a [`license_queues`](#license_queues).
- ☑️ shelf_created
  > `card_links.shelf_created` · `bool` · opcional
- 🧩 card_status
  > `card_links.card_status` · `jsonb` · obligatoria
- 🔗 budget_id <!-- orbit link: "#budget_settings", status: "clave foránea" -->
  > `card_links.budget_id` · `int8` · obligatoria
  >
  > Apunta a [`budget_settings`](#budget_settings).
- ☑️ review_position
  > `card_links.review_position` · `bool` · obligatoria
- 🧩 donation_source
  > `card_links.donation_source` · `jsonb` · opcional
- 🔤 series_ended
  > `card_links.series_ended` · `varchar` · opcional
- 🔗 donation_id <!-- orbit link: "#donation_history", status: "clave foránea" -->
  > `card_links.donation_id` · `int8` · obligatoria
  >
  > Apunta a [`donation_history`](#donation_history).
- 🔢 hold_count
  > `card_links.hold_count` · `int4` · obligatoria
- 🔤 loan_total
  > `card_links.loan_total` · `varchar` · obligatoria
- 🔗 order_id <!-- orbit link: "#order_items", status: "clave foránea" -->
  > `card_links.order_id` · `int8` · obligatoria
  >
  > Apunta a [`order_items`](#order_items).
- 🔤 room_ended
  > `card_links.room_ended` · `varchar` · opcional
- 🕒 device_created
  > `card_links.device_created` · `timestamp` · obligatoria
- 🔢 order_count
  > `card_links.order_count` · `int4` · obligatoria

### budget_queues
<!-- orbit
id: "budget_queues"
icon: "📇"
collapsed: true
-->
> `budget_queues` · tabla de Socios
>
> Una fila por presupuesto, con 16 columnas y unas 189 mil filas.
>
> **Pertenece a** [`license_queues_2`](#license_queues_2) · [`loan_logs_2`](#loan_logs_2)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `budget_queues.id` · `int8` · obligatoria
- 🔗 copy_id <!-- orbit link: "#copy_links", status: "clave foránea" -->
  > `budget_queues.copy_id` · `int8` · obligatoria
  >
  > Apunta a [`copy_links`](#copy_links).
- ☑️ member_archived
  > `budget_queues.member_archived` · `bool` · opcional
- 🧩 room_priority
  > `budget_queues.room_priority` · `jsonb` · obligatoria
- 🧩 tag_priority
  > `budget_queues.tag_priority` · `jsonb` · obligatoria
- 🔗 payment_id <!-- orbit link: "#payment_settings", status: "clave foránea" -->
  > `budget_queues.payment_id` · `int8` · obligatoria
  >
  > Apunta a [`payment_settings`](#payment_settings).
- 🔢 repair_notes
  > `budget_queues.repair_notes` · `int4` · obligatoria
- 🧩 book_archived
  > `budget_queues.book_archived` · `jsonb` · opcional
- 🔗 review_id <!-- orbit link: "#review_counts", status: "clave foránea" -->
  > `budget_queues.review_id` · `int8` · obligatoria
  >
  > Apunta a [`review_counts`](#review_counts).
- 🔢 budget_language
  > `budget_queues.budget_language` · `int4` · obligatoria
- 🧩 payment_reviewed
  > `budget_queues.payment_reviewed` · `jsonb` · opcional
- ☑️ vendor_created
  > `budget_queues.vendor_created` · `bool` · opcional
- 🔢 event_active
  > `budget_queues.event_active` · `int4` · obligatoria
- ☑️ book_name
  > `budget_queues.book_name` · `bool` · obligatoria
- 🕒 fee_kind
  > `budget_queues.fee_kind` · `timestamp` · obligatoria
- 🔗 tag_id <!-- orbit link: "#tag_settings_2", status: "clave foránea" -->
  > `budget_queues.tag_id` · `int8` · obligatoria
  >
  > Apunta a [`tag_settings_2`](#tag_settings_2).

### audit_links
<!-- orbit
id: "audit_links"
icon: "📗"
collapsed: true
-->
> `audit_links` · tabla de Socios
>
> Una fila por auditoría, con 21 columnas y unas 25 mil filas.
>
> **Pertenece a** [`budget_settings`](#budget_settings) · [`donation_history`](#donation_history) · [`book_items`](#book_items) · [`donation_queues`](#donation_queues)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `audit_links.id` · `int8` · obligatoria
- 🕒 loan_updated
  > `audit_links.loan_updated` · `timestamp` · opcional
- 🧩 notice_updated
  > `audit_links.notice_updated` · `jsonb` · obligatoria
- 🕒 device_kind
  > `audit_links.device_kind` · `timestamp` · opcional
- 🧩 series_ended
  > `audit_links.series_ended` · `jsonb` · opcional
- ☑️ card_position
  > `audit_links.card_position` · `bool` · obligatoria
- 🔗 shelf_id <!-- orbit link: "#shelf_queues", status: "clave foránea" -->
  > `audit_links.shelf_id` · `int8` · obligatoria
  >
  > Apunta a [`shelf_queues`](#shelf_queues).
- 🔢 payment_amount
  > `audit_links.payment_amount` · `int4` · obligatoria
- ☑️ book_status
  > `audit_links.book_status` · `bool` · obligatoria
- 🔤 donation_format
  > `audit_links.donation_format` · `varchar` · obligatoria
- 🔤 donation_state
  > `audit_links.donation_state` · `varchar` · obligatoria
- 🔤 shelf_created
  > `audit_links.shelf_created` · `varchar` · opcional
- 🔤 tag_region
  > `audit_links.tag_region` · `varchar` · obligatoria
- 🧩 copy_label
  > `audit_links.copy_label` · `jsonb` · opcional
- 🔤 order_name
  > `audit_links.order_name` · `varchar` · obligatoria
- ☑️ shelf_reviewed
  > `audit_links.shelf_reviewed` · `bool` · obligatoria
- 🔢 payment_source
  > `audit_links.payment_source` · `int4` · opcional
- 🧩 device_label
  > `audit_links.device_label` · `jsonb` · opcional
- 🔤 notice_position
  > `audit_links.notice_position` · `varchar` · obligatoria
- 🔢 device_count
  > `audit_links.device_count` · `int4` · opcional
- 🔗 review_id <!-- orbit link: "#review_versions", status: "clave foránea" -->
  > `audit_links.review_id` · `int8` · obligatoria
  >
  > Apunta a [`review_versions`](#review_versions).

## 🔁 Préstamos y devoluciones
<!-- orbit
id: "area-loans-returns"
color: "#2E9E5B"
-->
> 9 tablas. Las más grandes: [`series_counts`](#series_counts), [`audit_links_2`](#audit_links_2), [`notice_counts`](#notice_counts).
>
> **Relacionada con** [`branchs`](#branchs) · [`budget_queues`](#budget_queues) · [`license_settings`](#license_settings)

### series_counts
<!-- orbit
id: "series_counts"
icon: "📇"
collapsed: true
-->
> `series_counts` · tabla de Préstamos y devoluciones
>
> Una fila por serie, con 15 columnas y unas 468 mil filas.
>
> **Pertenece a** [`holds`](#holds)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `series_counts.id` · `int8` · obligatoria
- 🕒 loan_format
  > `series_counts.loan_format` · `timestamp` · opcional
- 🔗 budget_id <!-- orbit link: "#budget_settings_2", status: "clave foránea" -->
  > `series_counts.budget_id` · `int8` · obligatoria
  >
  > Apunta a [`budget_settings_2`](#budget_settings_2).
- 🔗 member_id <!-- orbit link: "#member_counts_2", status: "clave foránea" -->
  > `series_counts.member_id` · `int8` · obligatoria
  >
  > Apunta a [`member_counts_2`](#member_counts_2).
- 🔗 payment_id <!-- orbit link: "#payment_versions", status: "clave foránea" -->
  > `series_counts.payment_id` · `int8` · obligatoria
  >
  > Apunta a [`payment_versions`](#payment_versions).
- 🔗 license_id <!-- orbit link: "#license_queues", status: "clave foránea" -->
  > `series_counts.license_id` · `int8` · obligatoria
  >
  > Apunta a [`license_queues`](#license_queues).
- 🔢 copy_priority
  > `series_counts.copy_priority` · `int4` · obligatoria
- 🕒 card_title
  > `series_counts.card_title` · `timestamp` · obligatoria
- 🧩 role_title
  > `series_counts.role_title` · `jsonb` · obligatoria
- 🕒 book_priority
  > `series_counts.book_priority` · `timestamp` · obligatoria
- 🔢 fee_label
  > `series_counts.fee_label` · `int4` · opcional
- 🕒 staff_format
  > `series_counts.staff_format` · `timestamp` · obligatoria
- 🕒 notice_state
  > `series_counts.notice_state` · `timestamp` · obligatoria
- 🔗 donation_id <!-- orbit link: "#donation_links", status: "clave foránea" -->
  > `series_counts.donation_id` · `int8` · obligatoria
  >
  > Apunta a [`donation_links`](#donation_links).
- 🔤 copy_created
  > `series_counts.copy_created` · `varchar` · opcional

### audit_links_2
<!-- orbit
id: "audit_links_2"
icon: "📗"
collapsed: true
-->
> `audit_links_2` · tabla de Préstamos y devoluciones
>
> Una fila por auditoría, con 21 columnas y unas 2 mil filas.
>
> **Pertenece a** [`report_queues`](#report_queues) · [`license_settings`](#license_settings) · [`tag_counts`](#tag_counts)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `audit_links_2.id` · `int8` · obligatoria
- 🔤 license_ended
  > `audit_links_2.license_ended` · `varchar` · opcional
- 🔤 member_count
  > `audit_links_2.member_count` · `varchar` · obligatoria
- ☑️ event_format
  > `audit_links_2.event_format` · `bool` · obligatoria
- 🔤 member_created
  > `audit_links_2.member_created` · `varchar` · obligatoria
- 🔗 fee_id <!-- orbit link: "#fee_requests", status: "clave foránea" -->
  > `audit_links_2.fee_id` · `int8` · obligatoria
  >
  > Apunta a [`fee_requests`](#fee_requests).
- 🕒 fee_ended
  > `audit_links_2.fee_ended` · `timestamp` · obligatoria
- 🔗 card_id <!-- orbit link: "#card_logs", status: "clave foránea" -->
  > `audit_links_2.card_id` · `int8` · obligatoria
  >
  > Apunta a [`card_logs`](#card_logs).
- 🔢 visit_source
  > `audit_links_2.visit_source` · `int4` · opcional
- 🔢 repair_region
  > `audit_links_2.repair_region` · `int4` · opcional
- ☑️ card_language
  > `audit_links_2.card_language` · `bool` · obligatoria
- 🕒 series_created
  > `audit_links_2.series_created` · `timestamp` · opcional
- 🔤 donation_region
  > `audit_links_2.donation_region` · `varchar` · opcional
- ☑️ staff_name
  > `audit_links_2.staff_name` · `bool` · opcional
- 🧩 report_kind
  > `audit_links_2.report_kind` · `jsonb` · opcional
- ☑️ loan_archived
  > `audit_links_2.loan_archived` · `bool` · obligatoria
- 🔗 budget_id <!-- orbit link: "#budget_settings", status: "clave foránea" -->
  > `audit_links_2.budget_id` · `int8` · obligatoria
  >
  > Apunta a [`budget_settings`](#budget_settings).
- ☑️ branch_region
  > `audit_links_2.branch_region` · `bool` · obligatoria
- 🔗 visit_id <!-- orbit link: "#visit_queues", status: "clave foránea" -->
  > `audit_links_2.visit_id` · `int8` · obligatoria
  >
  > Apunta a [`visit_queues`](#visit_queues).
- 🕒 card_language_2
  > `audit_links_2.card_language_2` · `timestamp` · opcional
- 🔢 author_source
  > `audit_links_2.author_source` · `int4` · obligatoria

### notice_counts
<!-- orbit
id: "notice_counts"
icon: "🗂️"
collapsed: true
-->
> `notice_counts` · tabla de Préstamos y devoluciones
>
> Una fila por aviso, con 15 columnas y unas 426 mil filas.
>
> **Pertenece a** [`ebook_logs`](#ebook_logs) · [`device_items`](#device_items) · [`book_links`](#book_links)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `notice_counts.id` · `int8` · obligatoria
- 🔢 report_archived
  > `notice_counts.report_archived` · `int4` · obligatoria
- 🔤 role_status
  > `notice_counts.role_status` · `varchar` · obligatoria
- ☑️ visit_active
  > `notice_counts.visit_active` · `bool` · obligatoria
- 🧩 visit_external
  > `notice_counts.visit_external` · `jsonb` · obligatoria
- 🧩 shelf_started
  > `notice_counts.shelf_started` · `jsonb` · obligatoria
- 🕒 author_amount
  > `notice_counts.author_amount` · `timestamp` · opcional
- 🧩 tag_source
  > `notice_counts.tag_source` · `jsonb` · obligatoria
- 🕒 review_name
  > `notice_counts.review_name` · `timestamp` · opcional
- 🔗 hold_id <!-- orbit link: "#hold_links", status: "clave foránea" -->
  > `notice_counts.hold_id` · `int8` · obligatoria
  >
  > Apunta a [`hold_links`](#hold_links).
- ☑️ member_label
  > `notice_counts.member_label` · `bool` · opcional
- 🔗 rooms_id <!-- orbit link: "#rooms", status: "clave foránea" -->
  > `notice_counts.rooms_id` · `int8` · obligatoria
  >
  > Apunta a [`rooms`](#rooms).
- 🕒 event_state
  > `notice_counts.event_state` · `timestamp` · obligatoria
- ☑️ payment_label
  > `notice_counts.payment_label` · `bool` · obligatoria
- 🧩 visit_reviewed
  > `notice_counts.visit_reviewed` · `jsonb` · obligatoria

### shelfs
<!-- orbit
id: "shelfs"
icon: "📇"
collapsed: true
-->
> `shelfs` · tabla de Préstamos y devoluciones
>
> Una fila por estante, con 4 columnas y unas 423 mil filas.
>
> **Pertenece a** [`tag_counts`](#tag_counts)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `shelfs.id` · `int8` · obligatoria
- 🕒 role_started
  > `shelfs.role_started` · `timestamp` · opcional
- 🔤 donation_public
  > `shelfs.donation_public` · `varchar` · opcional
- 🕒 audit_count
  > `shelfs.audit_count` · `timestamp` · obligatoria

### tag_settings
<!-- orbit
id: "tag_settings"
icon: "📇"
collapsed: true
-->
> `tag_settings` · tabla de Préstamos y devoluciones
>
> Una fila por etiqueta, con 4 columnas y unas 784 mil filas.
>
> **Pertenece a** [`author_links_2`](#author_links_2) · [`fee_requests`](#fee_requests) · [`loan_logs_2`](#loan_logs_2) · [`card_queues`](#card_queues)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `tag_settings.id` · `int8` · obligatoria
- 🧩 tag_status
  > `tag_settings.tag_status` · `jsonb` · opcional
- 🔢 fee_state
  > `tag_settings.fee_state` · `int4` · obligatoria
- 🔢 notice_reviewed
  > `tag_settings.notice_reviewed` · `int4` · obligatoria

### staff_counts
<!-- orbit
id: "staff_counts"
icon: "📗"
collapsed: true
-->
> `staff_counts` · tabla de Préstamos y devoluciones
>
> Una fila por empleado, con 24 columnas y unas 697 mil filas.
>
> **Pertenece a** [`copy_links`](#copy_links) · [`ebook_queues`](#ebook_queues)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `staff_counts.id` · `int8` · obligatoria
- 🧩 role_reviewed
  > `staff_counts.role_reviewed` · `jsonb` · opcional
- 🔤 shelf_updated
  > `staff_counts.shelf_updated` · `varchar` · opcional
- 🧩 member_created
  > `staff_counts.member_created` · `jsonb` · opcional
- 🕒 role_language
  > `staff_counts.role_language` · `timestamp` · opcional
- 🔢 hold_updated
  > `staff_counts.hold_updated` · `int4` · obligatoria
- 🔤 report_total
  > `staff_counts.report_total` · `varchar` · obligatoria
- 🔢 branch_public
  > `staff_counts.branch_public` · `int4` · obligatoria
- ☑️ tag_created
  > `staff_counts.tag_created` · `bool` · obligatoria
- 🔗 holds_id <!-- orbit link: "#holds", status: "clave foránea" -->
  > `staff_counts.holds_id` · `int8` · obligatoria
  >
  > Apunta a [`holds`](#holds).
- ☑️ loan_created
  > `staff_counts.loan_created` · `bool` · obligatoria
- 🔢 review_region
  > `staff_counts.review_region` · `int4` · obligatoria
- 🔤 series_language
  > `staff_counts.series_language` · `varchar` · obligatoria
- 🔢 donation_state
  > `staff_counts.donation_state` · `int4` · obligatoria
- 🔤 series_ended
  > `staff_counts.series_ended` · `varchar` · opcional
- 🔗 loan_id <!-- orbit link: "#loan_counts", status: "clave foránea" -->
  > `staff_counts.loan_id` · `int8` · obligatoria
  >
  > Apunta a [`loan_counts`](#loan_counts).
- ☑️ audit_started
  > `staff_counts.audit_started` · `bool` · opcional
- 🧩 donation_amount
  > `staff_counts.donation_amount` · `jsonb` · obligatoria
- ☑️ vendor_active
  > `staff_counts.vendor_active` · `bool` · obligatoria
- 🧩 order_reviewed
  > `staff_counts.order_reviewed` · `jsonb` · obligatoria
- ☑️ author_position
  > `staff_counts.author_position` · `bool` · opcional
- 🕒 review_count
  > `staff_counts.review_count` · `timestamp` · obligatoria
- 🔢 room_count
  > `staff_counts.room_count` · `int4` · obligatoria
- 🔗 license_id <!-- orbit link: "#license_queues", status: "clave foránea" -->
  > `staff_counts.license_id` · `int8` · obligatoria
  >
  > Apunta a [`license_queues`](#license_queues).

### audit_settings
<!-- orbit
id: "audit_settings"
icon: "📗"
collapsed: true
-->
> `audit_settings` · tabla de Préstamos y devoluciones
>
> Una fila por auditoría, con 20 columnas y unas 644 mil filas.
>
> **Pertenece a** [`staff_settings`](#staff_settings)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `audit_settings.id` · `int8` · obligatoria
- 🕒 device_priority
  > `audit_settings.device_priority` · `timestamp` · opcional
- 🔗 tag_id <!-- orbit link: "#tag_settings", status: "clave foránea" -->
  > `audit_settings.tag_id` · `int8` · obligatoria
  >
  > Apunta a [`tag_settings`](#tag_settings).
- 🧩 tag_kind
  > `audit_settings.tag_kind` · `jsonb` · opcional
- 🔗 member_id <!-- orbit link: "#member_counts", status: "clave foránea" -->
  > `audit_settings.member_id` · `int8` · obligatoria
  >
  > Apunta a [`member_counts`](#member_counts).
- 🧩 repair_language
  > `audit_settings.repair_language` · `jsonb` · opcional
- 🕒 ebook_priority
  > `audit_settings.ebook_priority` · `timestamp` · obligatoria
- 🔤 ebook_updated
  > `audit_settings.ebook_updated` · `varchar` · opcional
- 🧩 repair_total
  > `audit_settings.repair_total` · `jsonb` · opcional
- ☑️ member_total
  > `audit_settings.member_total` · `bool` · opcional
- 🔗 order_id <!-- orbit link: "#order_versions", status: "clave foránea" -->
  > `audit_settings.order_id` · `int8` · obligatoria
  >
  > Apunta a [`order_versions`](#order_versions).
- 🧩 audit_format
  > `audit_settings.audit_format` · `jsonb` · opcional
- 🕒 review_region
  > `audit_settings.review_region` · `timestamp` · opcional
- 🔗 budget_id <!-- orbit link: "#budget_queues", status: "clave foránea" -->
  > `audit_settings.budget_id` · `int8` · obligatoria
  >
  > Apunta a [`budget_queues`](#budget_queues).
- 🔢 room_archived
  > `audit_settings.room_archived` · `int4` · obligatoria
- ☑️ member_label
  > `audit_settings.member_label` · `bool` · opcional
- 🔗 event_id <!-- orbit link: "#event_queues", status: "clave foránea" -->
  > `audit_settings.event_id` · `int8` · obligatoria
  >
  > Apunta a [`event_queues`](#event_queues).
- 🔤 notice_region
  > `audit_settings.notice_region` · `varchar` · obligatoria
- 🔗 license_id <!-- orbit link: "#license_settings", status: "clave foránea" -->
  > `audit_settings.license_id` · `int8` · obligatoria
  >
  > Apunta a [`license_settings`](#license_settings).
- 🔗 card_id <!-- orbit link: "#card_links", status: "clave foránea" -->
  > `audit_settings.card_id` · `int8` · obligatoria
  >
  > Apunta a [`card_links`](#card_links).

### review_items
<!-- orbit
id: "review_items"
icon: "🧷"
collapsed: true
-->
> `review_items` · tabla de Préstamos y devoluciones
>
> Una fila por reseña, con 24 columnas y unas 74 mil filas.
>
> **Pertenece a** [`donations`](#donations)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `review_items.id` · `int8` · obligatoria
- 🔗 licenses_id <!-- orbit link: "#licenses", status: "clave foránea" -->
  > `review_items.licenses_id` · `int8` · obligatoria
  >
  > Apunta a [`licenses`](#licenses).
- 🔢 copy_external
  > `review_items.copy_external` · `int4` · opcional
- 🧩 event_label
  > `review_items.event_label` · `jsonb` · obligatoria
- 🔢 role_language
  > `review_items.role_language` · `int4` · opcional
- ☑️ budget_reviewed
  > `review_items.budget_reviewed` · `bool` · obligatoria
- 🔗 loan_id <!-- orbit link: "#loan_history", status: "clave foránea" -->
  > `review_items.loan_id` · `int8` · obligatoria
  >
  > Apunta a [`loan_history`](#loan_history).
- 🔤 ebook_name
  > `review_items.ebook_name` · `varchar` · obligatoria
- 🔗 visit_id <!-- orbit link: "#visit_links", status: "clave foránea" -->
  > `review_items.visit_id` · `int8` · obligatoria
  >
  > Apunta a [`visit_links`](#visit_links).
- 🔢 payment_active
  > `review_items.payment_active` · `int4` · opcional
- ☑️ shelf_source
  > `review_items.shelf_source` · `bool` · obligatoria
- 🧩 shelf_reviewed
  > `review_items.shelf_reviewed` · `jsonb` · opcional
- 🔢 series_name
  > `review_items.series_name` · `int4` · opcional
- ☑️ donation_source
  > `review_items.donation_source` · `bool` · obligatoria
- 🔤 copy_reviewed
  > `review_items.copy_reviewed` · `varchar` · obligatoria
- 🔗 audit_id <!-- orbit link: "#audit_links", status: "clave foránea" -->
  > `review_items.audit_id` · `int8` · obligatoria
  >
  > Apunta a [`audit_links`](#audit_links).
- 🕒 copy_state
  > `review_items.copy_state` · `timestamp` · opcional
- 🔗 member_id <!-- orbit link: "#member_history", status: "clave foránea" -->
  > `review_items.member_id` · `int8` · obligatoria
  >
  > Apunta a [`member_history`](#member_history).
- ☑️ license_total
  > `review_items.license_total` · `bool` · obligatoria
- 🔗 role_id <!-- orbit link: "#role_counts", status: "clave foránea" -->
  > `review_items.role_id` · `int8` · obligatoria
  >
  > Apunta a [`role_counts`](#role_counts).
- 🔢 branch_created
  > `review_items.branch_created` · `int4` · opcional
- ☑️ card_created
  > `review_items.card_created` · `bool` · opcional
- ☑️ room_total
  > `review_items.room_total` · `bool` · opcional
- 🔤 report_started
  > `review_items.report_started` · `varchar` · obligatoria

### staff_items
<!-- orbit
id: "staff_items"
icon: "📗"
collapsed: true
-->
> `staff_items` · tabla de Préstamos y devoluciones
>
> Una fila por empleado, con 12 columnas y unas 118 mil filas.
>
> **Pertenece a** [`audit_links_2`](#audit_links_2) · [`copy_versions`](#copy_versions) · [`budgets`](#budgets)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `staff_items.id` · `int8` · obligatoria
- 🔢 device_source
  > `staff_items.device_source` · `int4` · obligatoria
- 🔢 staff_state
  > `staff_items.staff_state` · `int4` · obligatoria
- 🧩 review_notes
  > `staff_items.review_notes` · `jsonb` · opcional
- 🧩 vendor_total
  > `staff_items.vendor_total` · `jsonb` · obligatoria
- 🔢 tag_region
  > `staff_items.tag_region` · `int4` · obligatoria
- 🔢 branch_total
  > `staff_items.branch_total` · `int4` · opcional
- 🔤 ebook_amount
  > `staff_items.ebook_amount` · `varchar` · obligatoria
- 🕒 payment_position
  > `staff_items.payment_position` · `timestamp` · opcional
- 🔢 role_position
  > `staff_items.role_position` · `int4` · opcional
- 🔤 copy_state
  > `staff_items.copy_state` · `varchar` · opcional
- ☑️ ebook_code
  > `staff_items.ebook_code` · `bool` · opcional

## 🏛️ Sucursales
<!-- orbit
id: "area-branches"
color: "#8E4EC6"
-->
> 10 tablas. Las más grandes: [`tag_counts`](#tag_counts), [`role_counts`](#role_counts), [`branch_links`](#branch_links).
>
> **Relacionada con** [`loan_logs`](#loan_logs) · [`copy_versions`](#copy_versions) · [`vendor_items`](#vendor_items)

### tag_counts
<!-- orbit
id: "tag_counts"
icon: "🧷"
collapsed: true
-->
> `tag_counts` · tabla de Sucursales
>
> Una fila por etiqueta, con 17 columnas y unas 389 mil filas.
>
> **Pertenece a** [`review_versions`](#review_versions) · [`member_counts_2`](#member_counts_2) · [`hold_history`](#hold_history)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `tag_counts.id` · `int8` · obligatoria
- 🧩 member_updated
  > `tag_counts.member_updated` · `jsonb` · opcional
- 🔗 review_id <!-- orbit link: "#review_items", status: "clave foránea" -->
  > `tag_counts.review_id` · `int8` · obligatoria
  >
  > Apunta a [`review_items`](#review_items).
- 🔗 licenses_id <!-- orbit link: "#licenses", status: "clave foránea" -->
  > `tag_counts.licenses_id` · `int8` · obligatoria
  >
  > Apunta a [`licenses`](#licenses).
- 🕒 room_code
  > `tag_counts.room_code` · `timestamp` · opcional
- 🔢 shelf_status
  > `tag_counts.shelf_status` · `int4` · opcional
- 🔤 payment_count
  > `tag_counts.payment_count` · `varchar` · opcional
- 🔢 book_reviewed
  > `tag_counts.book_reviewed` · `int4` · opcional
- 🧩 room_public
  > `tag_counts.room_public` · `jsonb` · obligatoria
- 🔤 budget_archived
  > `tag_counts.budget_archived` · `varchar` · opcional
- 🔗 order_id <!-- orbit link: "#order_logs", status: "clave foránea" -->
  > `tag_counts.order_id` · `int8` · obligatoria
  >
  > Apunta a [`order_logs`](#order_logs).
- 🔗 ebook_id <!-- orbit link: "#ebook_queues", status: "clave foránea" -->
  > `tag_counts.ebook_id` · `int8` · obligatoria
  >
  > Apunta a [`ebook_queues`](#ebook_queues).
- 🔗 report_id <!-- orbit link: "#report_queues_2", status: "clave foránea" -->
  > `tag_counts.report_id` · `int8` · obligatoria
  >
  > Apunta a [`report_queues_2`](#report_queues_2).
- 🕒 shelf_name
  > `tag_counts.shelf_name` · `timestamp` · opcional
- 🔢 repair_public
  > `tag_counts.repair_public` · `int4` · opcional
- 🧩 license_reviewed
  > `tag_counts.license_reviewed` · `jsonb` · opcional
- 🔤 book_public
  > `tag_counts.book_public` · `varchar` · opcional

### role_counts
<!-- orbit
id: "role_counts"
icon: "📗"
collapsed: true
-->
> `role_counts` · tabla de Sucursales
>
> Una fila por rol, con 24 columnas y unas 767 mil filas.
>
> **Pertenece a** [`shelfs`](#shelfs) · [`visit_items`](#visit_items)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `role_counts.id` · `int8` · obligatoria
- ☑️ review_language
  > `role_counts.review_language` · `bool` · opcional
- ☑️ copy_name
  > `role_counts.copy_name` · `bool` · opcional
- 🔗 loan_id <!-- orbit link: "#loan_logs", status: "clave foránea" -->
  > `role_counts.loan_id` · `int8` · obligatoria
  >
  > Apunta a [`loan_logs`](#loan_logs).
- ☑️ member_name
  > `role_counts.member_name` · `bool` · obligatoria
- 🧩 order_reviewed
  > `role_counts.order_reviewed` · `jsonb` · opcional
- 🔤 shelf_active
  > `role_counts.shelf_active` · `varchar` · opcional
- 🔢 donation_position
  > `role_counts.donation_position` · `int4` · obligatoria
- 🔢 loan_priority
  > `role_counts.loan_priority` · `int4` · opcional
- 🔗 notice_id <!-- orbit link: "#notice_counts", status: "clave foránea" -->
  > `role_counts.notice_id` · `int8` · obligatoria
  >
  > Apunta a [`notice_counts`](#notice_counts).
- ☑️ notice_ended
  > `role_counts.notice_ended` · `bool` · obligatoria
- 🔢 tag_total
  > `role_counts.tag_total` · `int4` · opcional
- ☑️ vendor_priority
  > `role_counts.vendor_priority` · `bool` · obligatoria
- 🔗 hold_id <!-- orbit link: "#hold_logs", status: "clave foránea" -->
  > `role_counts.hold_id` · `int8` · obligatoria
  >
  > Apunta a [`hold_logs`](#hold_logs).
- 🔢 vendor_reviewed
  > `role_counts.vendor_reviewed` · `int4` · obligatoria
- 🔢 order_updated
  > `role_counts.order_updated` · `int4` · opcional
- 🔤 room_started
  > `role_counts.room_started` · `varchar` · obligatoria
- 🕒 license_source
  > `role_counts.license_source` · `timestamp` · opcional
- 🕒 loan_format
  > `role_counts.loan_format` · `timestamp` · opcional
- 🔢 series_notes
  > `role_counts.series_notes` · `int4` · opcional
- 🕒 report_public
  > `role_counts.report_public` · `timestamp` · obligatoria
- 🔗 fee_id <!-- orbit link: "#fee_requests", status: "clave foránea" -->
  > `role_counts.fee_id` · `int8` · obligatoria
  >
  > Apunta a [`fee_requests`](#fee_requests).
- ☑️ notice_created
  > `role_counts.notice_created` · `bool` · obligatoria
- 🕒 order_status
  > `role_counts.order_status` · `timestamp` · opcional

### branch_links
<!-- orbit
id: "branch_links"
icon: "📗"
collapsed: true
-->
> `branch_links` · tabla de Sucursales
>
> Una fila por sucursal, con 20 columnas y unas 423 mil filas.
>
> **Pertenece a** [`room_counts`](#room_counts)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `branch_links.id` · `int8` · obligatoria
- 🔗 author_id <!-- orbit link: "#author_history", status: "clave foránea" -->
  > `branch_links.author_id` · `int8` · obligatoria
  >
  > Apunta a [`author_history`](#author_history).
- 🔗 report_id <!-- orbit link: "#report_items", status: "clave foránea" -->
  > `branch_links.report_id` · `int8` · obligatoria
  >
  > Apunta a [`report_items`](#report_items).
- ☑️ staff_kind
  > `branch_links.staff_kind` · `bool` · opcional
- 🕒 hold_language
  > `branch_links.hold_language` · `timestamp` · opcional
- 🔢 loan_archived
  > `branch_links.loan_archived` · `int4` · obligatoria
- ☑️ event_started
  > `branch_links.event_started` · `bool` · opcional
- 🔢 author_updated
  > `branch_links.author_updated` · `int4` · opcional
- 🔤 staff_reviewed
  > `branch_links.staff_reviewed` · `varchar` · opcional
- 🔗 repair_id <!-- orbit link: "#repair_requests", status: "clave foránea" -->
  > `branch_links.repair_id` · `int8` · obligatoria
  >
  > Apunta a [`repair_requests`](#repair_requests).
- ☑️ notice_code
  > `branch_links.notice_code` · `bool` · obligatoria
- 🧩 repair_total
  > `branch_links.repair_total` · `jsonb` · opcional
- 🧩 hold_created
  > `branch_links.hold_created` · `jsonb` · opcional
- 🔢 review_updated
  > `branch_links.review_updated` · `int4` · obligatoria
- ☑️ series_amount
  > `branch_links.series_amount` · `bool` · opcional
- 🔗 tags_id <!-- orbit link: "#tags", status: "clave foránea" -->
  > `branch_links.tags_id` · `int8` · obligatoria
  >
  > Apunta a [`tags`](#tags).
- 🔗 author_id_2 <!-- orbit link: "#author_links_2", status: "clave foránea" -->
  > `branch_links.author_id_2` · `int8` · obligatoria
  >
  > Apunta a [`author_links_2`](#author_links_2).
- 🔢 branch_active
  > `branch_links.branch_active` · `int4` · obligatoria
- 🔗 review_id <!-- orbit link: "#review_links", status: "clave foránea" -->
  > `branch_links.review_id` · `int8` · obligatoria
  >
  > Apunta a [`review_links`](#review_links).
- 🔢 shelf_status
  > `branch_links.shelf_status` · `int4` · obligatoria

### hold_links
<!-- orbit
id: "hold_links"
icon: "📇"
collapsed: true
-->
> `hold_links` · tabla de Sucursales
>
> Una fila por reserva, con 14 columnas y unas 194 mil filas.
>
> **Pertenece a** [`review_queues`](#review_queues) · [`order_items`](#order_items)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `hold_links.id` · `int8` · obligatoria
- 🔤 audit_external
  > `hold_links.audit_external` · `varchar` · obligatoria
- 🔢 review_ended
  > `hold_links.review_ended` · `int4` · opcional
- 🔢 copy_format
  > `hold_links.copy_format` · `int4` · opcional
- 🧩 notice_amount
  > `hold_links.notice_amount` · `jsonb` · obligatoria
- 🔤 vendor_state
  > `hold_links.vendor_state` · `varchar` · opcional
- 🧩 budget_amount
  > `hold_links.budget_amount` · `jsonb` · obligatoria
- 🕒 room_status
  > `hold_links.room_status` · `timestamp` · opcional
- 🔢 license_format
  > `hold_links.license_format` · `int4` · obligatoria
- 🔗 visit_id <!-- orbit link: "#visit_queues", status: "clave foránea" -->
  > `hold_links.visit_id` · `int8` · obligatoria
  >
  > Apunta a [`visit_queues`](#visit_queues).
- 🔤 repair_format
  > `hold_links.repair_format` · `varchar` · opcional
- 🔤 repair_public
  > `hold_links.repair_public` · `varchar` · opcional
- 🔢 copy_active
  > `hold_links.copy_active` · `int4` · obligatoria
- 🔤 branch_position
  > `hold_links.branch_position` · `varchar` · obligatoria

### hold_logs
<!-- orbit
id: "hold_logs"
icon: "📇"
collapsed: true
-->
> `hold_logs` · tabla de Sucursales
>
> Una fila por reserva, con 13 columnas y unas 798 mil filas.
>
> **Pertenece a** [`report_queues`](#report_queues) · [`tag_counts`](#tag_counts)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `hold_logs.id` · `int8` · obligatoria
- 🔗 copy_id <!-- orbit link: "#copy_history", status: "clave foránea" -->
  > `hold_logs.copy_id` · `int8` · obligatoria
  >
  > Apunta a [`copy_history`](#copy_history).
- 🔢 loan_total
  > `hold_logs.loan_total` · `int4` · opcional
- 🔤 order_reviewed
  > `hold_logs.order_reviewed` · `varchar` · opcional
- 🔤 hold_state
  > `hold_logs.hold_state` · `varchar` · obligatoria
- 🕒 report_format
  > `hold_logs.report_format` · `timestamp` · opcional
- 🔤 loan_title
  > `hold_logs.loan_title` · `varchar` · opcional
- 🕒 tag_source
  > `hold_logs.tag_source` · `timestamp` · obligatoria
- 🧩 card_updated
  > `hold_logs.card_updated` · `jsonb` · opcional
- 🕒 branch_archived
  > `hold_logs.branch_archived` · `timestamp` · obligatoria
- 🔢 license_external
  > `hold_logs.license_external` · `int4` · opcional
- 🔤 staff_public
  > `hold_logs.staff_public` · `varchar` · opcional
- 🔤 device_format
  > `hold_logs.device_format` · `varchar` · obligatoria

### holds
<!-- orbit
id: "holds"
icon: "🗃️"
collapsed: true
-->
> `holds` · tabla de Sucursales
>
> Una fila por reserva, con 7 columnas y unas 261 mil filas.
>
> **Pertenece a** [`fee_history`](#fee_history) · [`vendor_items`](#vendor_items) · [`notice_counts`](#notice_counts)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `holds.id` · `int8` · obligatoria
- 🕒 role_region
  > `holds.role_region` · `timestamp` · obligatoria
- ☑️ audit_kind
  > `holds.audit_kind` · `bool` · obligatoria
- 🔤 budget_total
  > `holds.budget_total` · `varchar` · opcional
- 🔢 visit_public
  > `holds.visit_public` · `int4` · opcional
- 🔤 shelf_ended
  > `holds.shelf_ended` · `varchar` · opcional
- 🔢 role_public
  > `holds.role_public` · `int4` · opcional

### card_queues
<!-- orbit
id: "card_queues"
icon: "🧷"
collapsed: true
-->
> `card_queues` · tabla de Sucursales
>
> Una fila por credencial, con 24 columnas y unas 413 mil filas.
>
> **Pertenece a** [`series_counts`](#series_counts) · [`report_logs_2`](#report_logs_2) · [`audit_queues`](#audit_queues)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `card_queues.id` · `int8` · obligatoria
- 🧩 fee_title
  > `card_queues.fee_title` · `jsonb` · obligatoria
- 🧩 visit_ended
  > `card_queues.visit_ended` · `jsonb` · opcional
- 🔗 book_id <!-- orbit link: "#book_settings", status: "clave foránea" -->
  > `card_queues.book_id` · `int8` · obligatoria
  >
  > Apunta a [`book_settings`](#book_settings).
- 🔢 payment_ended
  > `card_queues.payment_ended` · `int4` · opcional
- 🕒 room_active
  > `card_queues.room_active` · `timestamp` · obligatoria
- 🕒 staff_updated
  > `card_queues.staff_updated` · `timestamp` · opcional
- 🔗 budgets_id <!-- orbit link: "#budgets", status: "clave foránea" -->
  > `card_queues.budgets_id` · `int8` · obligatoria
  >
  > Apunta a [`budgets`](#budgets).
- 🧩 copy_state
  > `card_queues.copy_state` · `jsonb` · obligatoria
- 🕒 audit_total
  > `card_queues.audit_total` · `timestamp` · opcional
- 🕒 shelf_external
  > `card_queues.shelf_external` · `timestamp` · opcional
- ☑️ fee_code
  > `card_queues.fee_code` · `bool` · obligatoria
- 🔗 copy_id <!-- orbit link: "#copy_versions", status: "clave foránea" -->
  > `card_queues.copy_id` · `int8` · obligatoria
  >
  > Apunta a [`copy_versions`](#copy_versions).
- 🔢 staff_reviewed
  > `card_queues.staff_reviewed` · `int4` · opcional
- 🔢 visit_status
  > `card_queues.visit_status` · `int4` · obligatoria
- 🔢 vendor_format
  > `card_queues.vendor_format` · `int4` · opcional
- 🔤 member_active
  > `card_queues.member_active` · `varchar` · opcional
- ☑️ event_label
  > `card_queues.event_label` · `bool` · obligatoria
- 🔢 room_position
  > `card_queues.room_position` · `int4` · obligatoria
- 🔗 vendor_id <!-- orbit link: "#vendor_history", status: "clave foránea" -->
  > `card_queues.vendor_id` · `int8` · obligatoria
  >
  > Apunta a [`vendor_history`](#vendor_history).
- 🕒 order_external
  > `card_queues.order_external` · `timestamp` · opcional
- ☑️ fee_total
  > `card_queues.fee_total` · `bool` · opcional
- 🔤 repair_active
  > `card_queues.repair_active` · `varchar` · opcional
- 🕒 license_external
  > `card_queues.license_external` · `timestamp` · obligatoria

### hold_history
<!-- orbit
id: "hold_history"
icon: "🗃️"
collapsed: true
-->
> `hold_history` · tabla de Sucursales
>
> Una fila por reserva, con 14 columnas y unas 438 mil filas.
>
> **Pertenece a** [`review_items`](#review_items) · [`event_history`](#event_history) · [`repair_requests`](#repair_requests)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `hold_history.id` · `int8` · obligatoria
- ☑️ repair_active
  > `hold_history.repair_active` · `bool` · obligatoria
- 🔢 room_started
  > `hold_history.room_started` · `int4` · obligatoria
- 🔤 loan_amount
  > `hold_history.loan_amount` · `varchar` · opcional
- 🧩 branch_kind
  > `hold_history.branch_kind` · `jsonb` · obligatoria
- 🧩 visit_external
  > `hold_history.visit_external` · `jsonb` · obligatoria
- 🔢 member_kind
  > `hold_history.member_kind` · `int4` · obligatoria
- 🔢 series_label
  > `hold_history.series_label` · `int4` · obligatoria
- 🧩 donation_name
  > `hold_history.donation_name` · `jsonb` · obligatoria
- 🔢 visit_region
  > `hold_history.visit_region` · `int4` · obligatoria
- 🔢 shelf_status
  > `hold_history.shelf_status` · `int4` · opcional
- 🕒 series_count
  > `hold_history.series_count` · `timestamp` · opcional
- 🧩 notice_count
  > `hold_history.notice_count` · `jsonb` · opcional
- 🧩 review_amount
  > `hold_history.review_amount` · `jsonb` · opcional

### room_requests
<!-- orbit
id: "room_requests"
icon: "🗂️"
collapsed: true
-->
> `room_requests` · tabla de Sucursales
>
> Una fila por sala, con 20 columnas y unas 874 mil filas.
>
> **Pertenece a** [`tag_settings_2`](#tag_settings_2)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `room_requests.id` · `int8` · obligatoria
- 🧩 branch_code
  > `room_requests.branch_code` · `jsonb` · opcional
- ☑️ ebook_language
  > `room_requests.ebook_language` · `bool` · obligatoria
- 🔗 card_id <!-- orbit link: "#card_links", status: "clave foránea" -->
  > `room_requests.card_id` · `int8` · obligatoria
  >
  > Apunta a [`card_links`](#card_links).
- 🔤 staff_name
  > `room_requests.staff_name` · `varchar` · obligatoria
- ☑️ event_state
  > `room_requests.event_state` · `bool` · opcional
- 🕒 event_amount
  > `room_requests.event_amount` · `timestamp` · opcional
- 🧩 staff_status
  > `room_requests.staff_status` · `jsonb` · obligatoria
- ☑️ report_external
  > `room_requests.report_external` · `bool` · opcional
- 🔤 book_position
  > `room_requests.book_position` · `varchar` · opcional
- 🕒 report_ended
  > `room_requests.report_ended` · `timestamp` · opcional
- 🔗 license_id <!-- orbit link: "#license_versions", status: "clave foránea" -->
  > `room_requests.license_id` · `int8` · obligatoria
  >
  > Apunta a [`license_versions`](#license_versions).
- 🕒 fee_total
  > `room_requests.fee_total` · `timestamp` · opcional
- 🔤 event_total
  > `room_requests.event_total` · `varchar` · obligatoria
- 🔗 device_id <!-- orbit link: "#device_items", status: "clave foránea" -->
  > `room_requests.device_id` · `int8` · obligatoria
  >
  > Apunta a [`device_items`](#device_items).
- 🧩 room_state
  > `room_requests.room_state` · `jsonb` · obligatoria
- 🔢 repair_name
  > `room_requests.repair_name` · `int4` · obligatoria
- 🔗 device_id_2 <!-- orbit link: "#device_items", status: "clave foránea" -->
  > `room_requests.device_id_2` · `int8` · obligatoria
  >
  > Apunta a [`device_items`](#device_items).
- 🔢 tag_ended
  > `room_requests.tag_ended` · `int4` · opcional
- ☑️ notice_region
  > `room_requests.notice_region` · `bool` · obligatoria

### review_links
<!-- orbit
id: "review_links"
icon: "🧮"
collapsed: true
-->
> `review_links` · tabla de Sucursales
>
> Una fila por reseña, con 20 columnas y unas 531 mil filas.
>
> **Pertenece a** [`report_items`](#report_items) · [`copy_items`](#copy_items) · [`order_items`](#order_items)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `review_links.id` · `int8` · obligatoria
- 🔗 member_id <!-- orbit link: "#member_versions", status: "clave foránea" -->
  > `review_links.member_id` · `int8` · obligatoria
  >
  > Apunta a [`member_versions`](#member_versions).
- 🔗 visits_id <!-- orbit link: "#visits", status: "clave foránea" -->
  > `review_links.visits_id` · `int8` · obligatoria
  >
  > Apunta a [`visits`](#visits).
- 🔗 series_id <!-- orbit link: "#series_links", status: "clave foránea" -->
  > `review_links.series_id` · `int8` · obligatoria
  >
  > Apunta a [`series_links`](#series_links).
- 🔗 license_id <!-- orbit link: "#license_queues", status: "clave foránea" -->
  > `review_links.license_id` · `int8` · obligatoria
  >
  > Apunta a [`license_queues`](#license_queues).
- 🔢 hold_notes
  > `review_links.hold_notes` · `int4` · obligatoria
- 🔗 copy_id <!-- orbit link: "#copy_versions", status: "clave foránea" -->
  > `review_links.copy_id` · `int8` · obligatoria
  >
  > Apunta a [`copy_versions`](#copy_versions).
- 🔗 visits_id_2 <!-- orbit link: "#visits", status: "clave foránea" -->
  > `review_links.visits_id_2` · `int8` · obligatoria
  >
  > Apunta a [`visits`](#visits).
- 🕒 card_position
  > `review_links.card_position` · `timestamp` · opcional
- 🕒 shelf_source
  > `review_links.shelf_source` · `timestamp` · opcional
- ☑️ room_active
  > `review_links.room_active` · `bool` · opcional
- 🕒 audit_started
  > `review_links.audit_started` · `timestamp` · obligatoria
- 🧩 ebook_title
  > `review_links.ebook_title` · `jsonb` · obligatoria
- 🕒 card_language
  > `review_links.card_language` · `timestamp` · obligatoria
- 🕒 notice_reviewed
  > `review_links.notice_reviewed` · `timestamp` · obligatoria
- ☑️ author_active
  > `review_links.author_active` · `bool` · opcional
- 🔗 loan_id <!-- orbit link: "#loan_logs_2", status: "clave foránea" -->
  > `review_links.loan_id` · `int8` · obligatoria
  >
  > Apunta a [`loan_logs_2`](#loan_logs_2).
- ☑️ notice_count
  > `review_links.notice_count` · `bool` · opcional
- 🔤 author_region
  > `review_links.author_region` · `varchar` · obligatoria
- 🔗 report_id <!-- orbit link: "#report_links", status: "clave foránea" -->
  > `review_links.report_id` · `int8` · obligatoria
  >
  > Apunta a [`report_links`](#report_links).

## 🗓️ Eventos
<!-- orbit
id: "area-events"
color: "#E5484D"
-->
> 10 tablas. Las más grandes: [`author_links_2`](#author_links_2), [`shelf_queues`](#shelf_queues), [`fee_requests`](#fee_requests).
>
> **Relacionada con** [`order_logs`](#order_logs) · [`report_logs`](#report_logs) · [`ebook_requests`](#ebook_requests)

### author_links_2
<!-- orbit
id: "author_links_2"
icon: "🧷"
collapsed: true
-->
> `author_links_2` · tabla de Eventos
>
> Una fila por autor, con 260 columnas y unas 52 mil filas.
>
> **Pertenece a** [`device_settings`](#device_settings)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `author_links_2.id` · `int8` · obligatoria
- 🔗 order_id <!-- orbit link: "#order_links", status: "clave foránea" -->
  > `author_links_2.order_id` · `int8` · obligatoria
  >
  > Apunta a [`order_links`](#order_links).
- 🔗 hold_id <!-- orbit link: "#hold_history", status: "clave foránea" -->
  > `author_links_2.hold_id` · `int8` · obligatoria
  >
  > Apunta a [`hold_history`](#hold_history).
- ☑️ repair_format
  > `author_links_2.repair_format` · `bool` · obligatoria
- 🕒 ebook_count
  > `author_links_2.ebook_count` · `timestamp` · opcional
- 🔢 copy_priority
  > `author_links_2.copy_priority` · `int4` · opcional
- ☑️ book_notes
  > `author_links_2.book_notes` · `bool` · opcional
- 🔗 review_id <!-- orbit link: "#review_counts", status: "clave foránea" -->
  > `author_links_2.review_id` · `int8` · obligatoria
  >
  > Apunta a [`review_counts`](#review_counts).
- 🔤 role_active
  > `author_links_2.role_active` · `varchar` · obligatoria
- 🕒 license_region
  > `author_links_2.license_region` · `timestamp` · opcional
- 🕒 vendor_region
  > `author_links_2.vendor_region` · `timestamp` · opcional
- 🧩 notice_kind
  > `author_links_2.notice_kind` · `jsonb` · opcional
- 🕒 payment_kind
  > `author_links_2.payment_kind` · `timestamp` · obligatoria
- 🧩 visit_public
  > `author_links_2.visit_public` · `jsonb` · opcional
- 🕒 donation_status
  > `author_links_2.donation_status` · `timestamp` · obligatoria
- 🔗 ebook_id <!-- orbit link: "#ebook_requests", status: "clave foránea" -->
  > `author_links_2.ebook_id` · `int8` · obligatoria
  >
  > Apunta a [`ebook_requests`](#ebook_requests).
- 🕒 event_active
  > `author_links_2.event_active` · `timestamp` · obligatoria
- 🔗 visits_id <!-- orbit link: "#visits", status: "clave foránea" -->
  > `author_links_2.visits_id` · `int8` · obligatoria
  >
  > Apunta a [`visits`](#visits).
- 🔗 series_id <!-- orbit link: "#series_requests", status: "clave foránea" -->
  > `author_links_2.series_id` · `int8` · obligatoria
  >
  > Apunta a [`series_requests`](#series_requests).
- 🧩 notice_total
  > `author_links_2.notice_total` · `jsonb` · opcional
- 🧩 payment_ended
  > `author_links_2.payment_ended` · `jsonb` · opcional
- 🔢 visit_count
  > `author_links_2.visit_count` · `int4` · obligatoria
- 🕒 donation_position
  > `author_links_2.donation_position` · `timestamp` · obligatoria
- 🔗 copy_id <!-- orbit link: "#copy_versions", status: "clave foránea" -->
  > `author_links_2.copy_id` · `int8` · obligatoria
  >
  > Apunta a [`copy_versions`](#copy_versions).
- ☑️ book_label
  > `author_links_2.book_label` · `bool` · obligatoria
- 🕒 fee_label
  > `author_links_2.fee_label` · `timestamp` · opcional
- 🔢 budget_status
  > `author_links_2.budget_status` · `int4` · obligatoria
- 🔢 copy_source
  > `author_links_2.copy_source` · `int4` · obligatoria
- 🔤 audit_notes
  > `author_links_2.audit_notes` · `varchar` · obligatoria
- ☑️ review_title
  > `author_links_2.review_title` · `bool` · opcional
- 🕒 author_title
  > `author_links_2.author_title` · `timestamp` · opcional
- ☑️ notice_started
  > `author_links_2.notice_started` · `bool` · obligatoria
- 🔢 event_state
  > `author_links_2.event_state` · `int4` · opcional
- 🧩 visit_total
  > `author_links_2.visit_total` · `jsonb` · obligatoria
- 🔗 license_id <!-- orbit link: "#license_queues_2", status: "clave foránea" -->
  > `author_links_2.license_id` · `int8` · obligatoria
  >
  > Apunta a [`license_queues_2`](#license_queues_2).
- 🔗 report_id <!-- orbit link: "#report_queues", status: "clave foránea" -->
  > `author_links_2.report_id` · `int8` · obligatoria
  >
  > Apunta a [`report_queues`](#report_queues).
- 🔗 room_id <!-- orbit link: "#room_settings", status: "clave foránea" -->
  > `author_links_2.room_id` · `int8` · obligatoria
  >
  > Apunta a [`room_settings`](#room_settings).
- ☑️ series_status
  > `author_links_2.series_status` · `bool` · opcional
- ☑️ loan_notes
  > `author_links_2.loan_notes` · `bool` · obligatoria
- 🔢 branch_amount
  > `author_links_2.branch_amount` · `int4` · obligatoria
- ☑️ branch_priority
  > `author_links_2.branch_priority` · `bool` · opcional
- 🔤 tag_title
  > `author_links_2.tag_title` · `varchar` · opcional
- 🔗 seriess_id <!-- orbit link: "#seriess", status: "clave foránea" -->
  > `author_links_2.seriess_id` · `int8` · obligatoria
  >
  > Apunta a [`seriess`](#seriess).
- 🕒 device_ended
  > `author_links_2.device_ended` · `timestamp` · obligatoria
- 🧩 event_format
  > `author_links_2.event_format` · `jsonb` · obligatoria
- 🔗 review_id_2 <!-- orbit link: "#review_links", status: "clave foránea" -->
  > `author_links_2.review_id_2` · `int8` · obligatoria
  >
  > Apunta a [`review_links`](#review_links).
- 🧩 license_status
  > `author_links_2.license_status` · `jsonb` · opcional
- 🧩 card_notes
  > `author_links_2.card_notes` · `jsonb` · obligatoria
- ☑️ hold_notes
  > `author_links_2.hold_notes` · `bool` · opcional
- 🔢 role_reviewed
  > `author_links_2.role_reviewed` · `int4` · opcional
- ☑️ budget_position
  > `author_links_2.budget_position` · `bool` · opcional
- 🕒 role_language
  > `author_links_2.role_language` · `timestamp` · obligatoria
- 🧩 review_title_2
  > `author_links_2.review_title_2` · `jsonb` · obligatoria
- 🔗 license_id_2 <!-- orbit link: "#license_logs_2", status: "clave foránea" -->
  > `author_links_2.license_id_2` · `int8` · obligatoria
  >
  > Apunta a [`license_logs_2`](#license_logs_2).
- ☑️ order_total
  > `author_links_2.order_total` · `bool` · opcional
- 🔗 tag_id <!-- orbit link: "#tag_requests", status: "clave foránea" -->
  > `author_links_2.tag_id` · `int8` · obligatoria
  >
  > Apunta a [`tag_requests`](#tag_requests).
- 🕒 loan_code
  > `author_links_2.loan_code` · `timestamp` · opcional
- 🔗 loan_id <!-- orbit link: "#loan_history", status: "clave foránea" -->
  > `author_links_2.loan_id` · `int8` · obligatoria
  >
  > Apunta a [`loan_history`](#loan_history).
- 🧩 notice_updated
  > `author_links_2.notice_updated` · `jsonb` · obligatoria
- 🕒 hold_reviewed
  > `author_links_2.hold_reviewed` · `timestamp` · opcional
- 🧩 staff_position
  > `author_links_2.staff_position` · `jsonb` · opcional
- 🕒 hold_notes_2
  > `author_links_2.hold_notes_2` · `timestamp` · obligatoria
- 🔗 holds_id <!-- orbit link: "#holds", status: "clave foránea" -->
  > `author_links_2.holds_id` · `int8` · obligatoria
  >
  > Apunta a [`holds`](#holds).
- 🔗 event_id <!-- orbit link: "#event_history", status: "clave foránea" -->
  > `author_links_2.event_id` · `int8` · obligatoria
  >
  > Apunta a [`event_history`](#event_history).
- ☑️ event_ended
  > `author_links_2.event_ended` · `bool` · opcional
- 🔗 ebook_id_2 <!-- orbit link: "#ebook_requests", status: "clave foránea" -->
  > `author_links_2.ebook_id_2` · `int8` · obligatoria
  >
  > Apunta a [`ebook_requests`](#ebook_requests).
- 🔤 tag_total
  > `author_links_2.tag_total` · `varchar` · opcional
- 🕒 member_status
  > `author_links_2.member_status` · `timestamp` · opcional
- ☑️ device_region
  > `author_links_2.device_region` · `bool` · opcional
- 🔗 notice_id <!-- orbit link: "#notice_counts", status: "clave foránea" -->
  > `author_links_2.notice_id` · `int8` · obligatoria
  >
  > Apunta a [`notice_counts`](#notice_counts).
- 🕒 device_source
  > `author_links_2.device_source` · `timestamp` · opcional
- 🔢 event_label
  > `author_links_2.event_label` · `int4` · opcional
- ☑️ budget_count
  > `author_links_2.budget_count` · `bool` · obligatoria
- 🔗 review_id_3 <!-- orbit link: "#review_counts", status: "clave foránea" -->
  > `author_links_2.review_id_3` · `int8` · obligatoria
  >
  > Apunta a [`review_counts`](#review_counts).
- 🔢 role_source
  > `author_links_2.role_source` · `int4` · obligatoria
- 🕒 series_status_2
  > `author_links_2.series_status_2` · `timestamp` · obligatoria
- 🔗 review_id_4 <!-- orbit link: "#review_counts", status: "clave foránea" -->
  > `author_links_2.review_id_4` · `int8` · obligatoria
  >
  > Apunta a [`review_counts`](#review_counts).
- 🔗 report_id_2 <!-- orbit link: "#report_queues_2", status: "clave foránea" -->
  > `author_links_2.report_id_2` · `int8` · obligatoria
  >
  > Apunta a [`report_queues_2`](#report_queues_2).
- 🧩 author_updated
  > `author_links_2.author_updated` · `jsonb` · obligatoria
- ☑️ repair_format_2
  > `author_links_2.repair_format_2` · `bool` · opcional
- 🕒 tag_status
  > `author_links_2.tag_status` · `timestamp` · obligatoria
- 🔢 budget_kind
  > `author_links_2.budget_kind` · `int4` · obligatoria
- 🕒 shelf_updated
  > `author_links_2.shelf_updated` · `timestamp` · obligatoria
- 🕒 shelf_language
  > `author_links_2.shelf_language` · `timestamp` · obligatoria
- 🕒 visit_created
  > `author_links_2.visit_created` · `timestamp` · opcional
- 🔤 review_count
  > `author_links_2.review_count` · `varchar` · obligatoria
- 🔗 ebook_id_3 <!-- orbit link: "#ebook_logs", status: "clave foránea" -->
  > `author_links_2.ebook_id_3` · `int8` · obligatoria
  >
  > Apunta a [`ebook_logs`](#ebook_logs).
- 🔗 branch_id <!-- orbit link: "#branch_links", status: "clave foránea" -->
  > `author_links_2.branch_id` · `int8` · obligatoria
  >
  > Apunta a [`branch_links`](#branch_links).
- 🔗 order_id_2 <!-- orbit link: "#order_items", status: "clave foránea" -->
  > `author_links_2.order_id_2` · `int8` · obligatoria
  >
  > Apunta a [`order_items`](#order_items).
- 🔤 member_priority
  > `author_links_2.member_priority` · `varchar` · obligatoria
- 🔢 repair_reviewed
  > `author_links_2.repair_reviewed` · `int4` · obligatoria
- ☑️ event_position
  > `author_links_2.event_position` · `bool` · opcional
- 🕒 license_state
  > `author_links_2.license_state` · `timestamp` · opcional
- 🔤 series_source
  > `author_links_2.series_source` · `varchar` · opcional
- ☑️ fee_started
  > `author_links_2.fee_started` · `bool` · obligatoria
- 🔢 copy_updated
  > `author_links_2.copy_updated` · `int4` · obligatoria
- 🔤 order_priority
  > `author_links_2.order_priority` · `varchar` · opcional
- 🔗 room_id_2 <!-- orbit link: "#room_logs", status: "clave foránea" -->
  > `author_links_2.room_id_2` · `int8` · obligatoria
  >
  > Apunta a [`room_logs`](#room_logs).
- 🔗 branch_id_2 <!-- orbit link: "#branch_links", status: "clave foránea" -->
  > `author_links_2.branch_id_2` · `int8` · obligatoria
  >
  > Apunta a [`branch_links`](#branch_links).
- 🔤 card_amount
  > `author_links_2.card_amount` · `varchar` · opcional
- 🧩 event_title
  > `author_links_2.event_title` · `jsonb` · obligatoria
- 🧩 book_public
  > `author_links_2.book_public` · `jsonb` · obligatoria
- 🔗 book_id <!-- orbit link: "#book_links", status: "clave foránea" -->
  > `author_links_2.book_id` · `int8` · obligatoria
  >
  > Apunta a [`book_links`](#book_links).
- 🔗 rooms_id <!-- orbit link: "#rooms", status: "clave foránea" -->
  > `author_links_2.rooms_id` · `int8` · obligatoria
  >
  > Apunta a [`rooms`](#rooms).
- 🕒 shelf_amount
  > `author_links_2.shelf_amount` · `timestamp` · obligatoria
- 🕒 event_priority
  > `author_links_2.event_priority` · `timestamp` · obligatoria
- 🔤 member_archived
  > `author_links_2.member_archived` · `varchar` · obligatoria
- 🔗 card_id <!-- orbit link: "#card_queues", status: "clave foránea" -->
  > `author_links_2.card_id` · `int8` · obligatoria
  >
  > Apunta a [`card_queues`](#card_queues).
- 🔢 event_reviewed
  > `author_links_2.event_reviewed` · `int4` · opcional
- 🔤 series_active
  > `author_links_2.series_active` · `varchar` · obligatoria
- 🧩 budget_ended
  > `author_links_2.budget_ended` · `jsonb` · opcional
- 🔗 order_id_3 <!-- orbit link: "#order_links", status: "clave foránea" -->
  > `author_links_2.order_id_3` · `int8` · obligatoria
  >
  > Apunta a [`order_links`](#order_links).
- 🔤 review_ended
  > `author_links_2.review_ended` · `varchar` · opcional
- 🕒 notice_reviewed
  > `author_links_2.notice_reviewed` · `timestamp` · opcional
- 🔗 order_id_4 <!-- orbit link: "#order_links", status: "clave foránea" -->
  > `author_links_2.order_id_4` · `int8` · obligatoria
  >
  > Apunta a [`order_links`](#order_links).
- ☑️ tag_priority
  > `author_links_2.tag_priority` · `bool` · opcional
- 🕒 budget_count_2
  > `author_links_2.budget_count_2` · `timestamp` · opcional
- 🔢 author_title_2
  > `author_links_2.author_title_2` · `int4` · opcional
- ☑️ loan_public
  > `author_links_2.loan_public` · `bool` · opcional
- 🔗 copy_id_2 <!-- orbit link: "#copy_counts", status: "clave foránea" -->
  > `author_links_2.copy_id_2` · `int8` · obligatoria
  >
  > Apunta a [`copy_counts`](#copy_counts).
- 🧩 loan_region
  > `author_links_2.loan_region` · `jsonb` · opcional
- ☑️ loan_active
  > `author_links_2.loan_active` · `bool` · opcional
- 🔤 book_reviewed
  > `author_links_2.book_reviewed` · `varchar` · opcional
- 🧩 member_format
  > `author_links_2.member_format` · `jsonb` · opcional
- 🕒 report_status
  > `author_links_2.report_status` · `timestamp` · opcional
- ☑️ branch_format
  > `author_links_2.branch_format` · `bool` · obligatoria
- 🔢 hold_archived
  > `author_links_2.hold_archived` · `int4` · obligatoria
- 🔗 ebook_id_4 <!-- orbit link: "#ebook_logs", status: "clave foránea" -->
  > `author_links_2.ebook_id_4` · `int8` · obligatoria
  >
  > Apunta a [`ebook_logs`](#ebook_logs).
- 🔢 hold_position
  > `author_links_2.hold_position` · `int4` · obligatoria
- 🕒 role_updated
  > `author_links_2.role_updated` · `timestamp` · obligatoria
- 🔢 event_notes
  > `author_links_2.event_notes` · `int4` · opcional
- 🧩 hold_priority
  > `author_links_2.hold_priority` · `jsonb` · opcional
- 🧩 notice_reviewed_2
  > `author_links_2.notice_reviewed_2` · `jsonb` · obligatoria
- 🔢 report_state
  > `author_links_2.report_state` · `int4` · opcional
- ☑️ review_source
  > `author_links_2.review_source` · `bool` · opcional
- 🔢 budget_public
  > `author_links_2.budget_public` · `int4` · obligatoria
- 🔢 device_kind
  > `author_links_2.device_kind` · `int4` · opcional
- 🔤 event_amount
  > `author_links_2.event_amount` · `varchar` · obligatoria
- 🔤 payment_external
  > `author_links_2.payment_external` · `varchar` · obligatoria
- ☑️ report_notes
  > `author_links_2.report_notes` · `bool` · opcional
- ☑️ tag_position
  > `author_links_2.tag_position` · `bool` · opcional
- ☑️ room_priority
  > `author_links_2.room_priority` · `bool` · obligatoria
- 🕒 shelf_count
  > `author_links_2.shelf_count` · `timestamp` · obligatoria
- ☑️ review_priority
  > `author_links_2.review_priority` · `bool` · obligatoria
- 🔗 notice_id_2 <!-- orbit link: "#notice_counts", status: "clave foránea" -->
  > `author_links_2.notice_id_2` · `int8` · obligatoria
  >
  > Apunta a [`notice_counts`](#notice_counts).
- ☑️ payment_region
  > `author_links_2.payment_region` · `bool` · obligatoria
- ☑️ book_updated
  > `author_links_2.book_updated` · `bool` · opcional
- ☑️ review_status
  > `author_links_2.review_status` · `bool` · obligatoria
- 🔗 review_id_5 <!-- orbit link: "#review_logs", status: "clave foránea" -->
  > `author_links_2.review_id_5` · `int8` · obligatoria
  >
  > Apunta a [`review_logs`](#review_logs).
- 🔗 notice_id_3 <!-- orbit link: "#notice_counts", status: "clave foránea" -->
  > `author_links_2.notice_id_3` · `int8` · obligatoria
  >
  > Apunta a [`notice_counts`](#notice_counts).
- 🔗 member_id <!-- orbit link: "#member_history", status: "clave foránea" -->
  > `author_links_2.member_id` · `int8` · obligatoria
  >
  > Apunta a [`member_history`](#member_history).
- 🧩 notice_region
  > `author_links_2.notice_region` · `jsonb` · obligatoria
- 🔢 payment_region_2
  > `author_links_2.payment_region_2` · `int4` · obligatoria
- ☑️ vendor_kind
  > `author_links_2.vendor_kind` · `bool` · opcional
- 🧩 staff_public
  > `author_links_2.staff_public` · `jsonb` · obligatoria
- 🧩 shelf_started
  > `author_links_2.shelf_started` · `jsonb` · opcional
- 🔢 fee_format
  > `author_links_2.fee_format` · `int4` · opcional
- 🔤 visit_label
  > `author_links_2.visit_label` · `varchar` · opcional
- 🧩 room_name
  > `author_links_2.room_name` · `jsonb` · obligatoria
- 🧩 ebook_code
  > `author_links_2.ebook_code` · `jsonb` · obligatoria
- 🕒 ebook_count_2
  > `author_links_2.ebook_count_2` · `timestamp` · opcional
- ☑️ fee_kind
  > `author_links_2.fee_kind` · `bool` · opcional
- 🔢 role_status
  > `author_links_2.role_status` · `int4` · opcional
- 🔢 license_archived
  > `author_links_2.license_archived` · `int4` · obligatoria
- ☑️ copy_amount
  > `author_links_2.copy_amount` · `bool` · opcional
- 🔗 event_id_2 <!-- orbit link: "#event_history", status: "clave foránea" -->
  > `author_links_2.event_id_2` · `int8` · obligatoria
  >
  > Apunta a [`event_history`](#event_history).
- ☑️ ebook_updated
  > `author_links_2.ebook_updated` · `bool` · opcional
- 🔢 card_status
  > `author_links_2.card_status` · `int4` · obligatoria
- 🧩 tag_archived
  > `author_links_2.tag_archived` · `jsonb` · obligatoria
- 🔤 notice_code
  > `author_links_2.notice_code` · `varchar` · opcional
- 🔤 review_code
  > `author_links_2.review_code` · `varchar` · opcional
- 🧩 card_code
  > `author_links_2.card_code` · `jsonb` · obligatoria
- 🔤 book_active
  > `author_links_2.book_active` · `varchar` · obligatoria
- 🔢 license_started
  > `author_links_2.license_started` · `int4` · obligatoria
- 🔗 device_id <!-- orbit link: "#device_settings", status: "clave foránea" -->
  > `author_links_2.device_id` · `int8` · obligatoria
  >
  > Apunta a [`device_settings`](#device_settings).
- 🧩 report_language
  > `author_links_2.report_language` · `jsonb` · opcional
- 🔢 notice_amount
  > `author_links_2.notice_amount` · `int4` · obligatoria
- 🧩 event_source
  > `author_links_2.event_source` · `jsonb` · obligatoria
- 🧩 fee_position
  > `author_links_2.fee_position` · `jsonb` · obligatoria
- 🔤 member_public
  > `author_links_2.member_public` · `varchar` · opcional
- 🧩 notice_archived
  > `author_links_2.notice_archived` · `jsonb` · obligatoria
- 🧩 copy_kind
  > `author_links_2.copy_kind` · `jsonb` · obligatoria
- 🔢 order_active
  > `author_links_2.order_active` · `int4` · opcional
- 🧩 license_title
  > `author_links_2.license_title` · `jsonb` · opcional
- 🔤 event_name
  > `author_links_2.event_name` · `varchar` · opcional
- ☑️ vendor_source
  > `author_links_2.vendor_source` · `bool` · obligatoria
- 🔢 copy_name
  > `author_links_2.copy_name` · `int4` · opcional
- 🔗 visits_id_2 <!-- orbit link: "#visits", status: "clave foránea" -->
  > `author_links_2.visits_id_2` · `int8` · obligatoria
  >
  > Apunta a [`visits`](#visits).
- 🔢 order_title
  > `author_links_2.order_title` · `int4` · opcional
- ☑️ room_created
  > `author_links_2.room_created` · `bool` · opcional
- 🕒 branch_language
  > `author_links_2.branch_language` · `timestamp` · opcional
- 🕒 report_reviewed
  > `author_links_2.report_reviewed` · `timestamp` · obligatoria
- 🔤 hold_region
  > `author_links_2.hold_region` · `varchar` · opcional
- 🕒 series_total
  > `author_links_2.series_total` · `timestamp` · obligatoria
- ☑️ report_notes_2
  > `author_links_2.report_notes_2` · `bool` · opcional
- ☑️ payment_code
  > `author_links_2.payment_code` · `bool` · opcional
- 🔗 donation_id <!-- orbit link: "#donation_history", status: "clave foránea" -->
  > `author_links_2.donation_id` · `int8` · obligatoria
  >
  > Apunta a [`donation_history`](#donation_history).
- 🔤 payment_language
  > `author_links_2.payment_language` · `varchar` · opcional
- ☑️ member_format_2
  > `author_links_2.member_format_2` · `bool` · obligatoria
- 🔤 order_external
  > `author_links_2.order_external` · `varchar` · obligatoria
- 🕒 review_priority_2
  > `author_links_2.review_priority_2` · `timestamp` · opcional
- ☑️ fee_status
  > `author_links_2.fee_status` · `bool` · obligatoria
- ☑️ series_status_3
  > `author_links_2.series_status_3` · `bool` · obligatoria
- 🕒 card_status_2
  > `author_links_2.card_status_2` · `timestamp` · opcional
- 🕒 staff_active
  > `author_links_2.staff_active` · `timestamp` · obligatoria
- 🕒 series_language
  > `author_links_2.series_language` · `timestamp` · opcional
- 🔤 card_total
  > `author_links_2.card_total` · `varchar` · obligatoria
- 🧩 ebook_language
  > `author_links_2.ebook_language` · `jsonb` · opcional
- 🔗 audit_id <!-- orbit link: "#audit_links", status: "clave foránea" -->
  > `author_links_2.audit_id` · `int8` · obligatoria
  >
  > Apunta a [`audit_links`](#audit_links).
- 🔤 series_status_4
  > `author_links_2.series_status_4` · `varchar` · obligatoria
- 🔤 ebook_priority
  > `author_links_2.ebook_priority` · `varchar` · opcional
- 🔗 license_id_3 <!-- orbit link: "#license_versions", status: "clave foránea" -->
  > `author_links_2.license_id_3` · `int8` · obligatoria
  >
  > Apunta a [`license_versions`](#license_versions).
- 🕒 tag_count
  > `author_links_2.tag_count` · `timestamp` · obligatoria
- 🔤 tag_public
  > `author_links_2.tag_public` · `varchar` · obligatoria
- 🕒 order_status
  > `author_links_2.order_status` · `timestamp` · opcional
- 🔗 author_id <!-- orbit link: "#author_links_2", status: "clave foránea" -->
  > `author_links_2.author_id` · `int8` · obligatoria
  >
  > Apunta a [`author_links_2`](#author_links_2).
- 🧩 author_total
  > `author_links_2.author_total` · `jsonb` · obligatoria
- 🔢 order_label
  > `author_links_2.order_label` · `int4` · obligatoria
- 🔢 payment_updated
  > `author_links_2.payment_updated` · `int4` · obligatoria
- ☑️ branch_code
  > `author_links_2.branch_code` · `bool` · obligatoria
- ☑️ repair_created
  > `author_links_2.repair_created` · `bool` · opcional
- 🕒 report_started
  > `author_links_2.report_started` · `timestamp` · obligatoria
- 🔢 author_status
  > `author_links_2.author_status` · `int4` · obligatoria
- 🔤 member_kind
  > `author_links_2.member_kind` · `varchar` · obligatoria
- 🧩 visit_ended
  > `author_links_2.visit_ended` · `jsonb` · opcional
- 🧩 role_status_2
  > `author_links_2.role_status_2` · `jsonb` · obligatoria
- 🧩 staff_amount
  > `author_links_2.staff_amount` · `jsonb` · opcional
- 🔢 hold_public
  > `author_links_2.hold_public` · `int4` · obligatoria
- 🧩 notice_title
  > `author_links_2.notice_title` · `jsonb` · obligatoria
- 🔗 budget_id <!-- orbit link: "#budget_queues", status: "clave foránea" -->
  > `author_links_2.budget_id` · `int8` · obligatoria
  >
  > Apunta a [`budget_queues`](#budget_queues).
- 🔤 visit_format
  > `author_links_2.visit_format` · `varchar` · opcional
- 🧩 visit_region
  > `author_links_2.visit_region` · `jsonb` · obligatoria
- ☑️ notice_archived_2
  > `author_links_2.notice_archived_2` · `bool` · opcional
- 🔤 event_status
  > `author_links_2.event_status` · `varchar` · obligatoria
- 🔢 fee_name
  > `author_links_2.fee_name` · `int4` · obligatoria
- 🔗 licenses_id <!-- orbit link: "#licenses", status: "clave foránea" -->
  > `author_links_2.licenses_id` · `int8` · obligatoria
  >
  > Apunta a [`licenses`](#licenses).
- 🔤 review_label
  > `author_links_2.review_label` · `varchar` · opcional
- 🕒 series_reviewed
  > `author_links_2.series_reviewed` · `timestamp` · obligatoria
- 🕒 event_reviewed_2
  > `author_links_2.event_reviewed_2` · `timestamp` · opcional
- 🔗 budget_id_2 <!-- orbit link: "#budget_settings_2", status: "clave foránea" -->
  > `author_links_2.budget_id_2` · `int8` · obligatoria
  >
  > Apunta a [`budget_settings_2`](#budget_settings_2).
- 🕒 ebook_created
  > `author_links_2.ebook_created` · `timestamp` · opcional
- 🔗 audit_id_2 <!-- orbit link: "#audit_settings", status: "clave foránea" -->
  > `author_links_2.audit_id_2` · `int8` · obligatoria
  >
  > Apunta a [`audit_settings`](#audit_settings).
- 🔤 author_created
  > `author_links_2.author_created` · `varchar` · obligatoria
- 🔤 event_external
  > `author_links_2.event_external` · `varchar` · obligatoria
- 🔤 series_updated
  > `author_links_2.series_updated` · `varchar` · opcional
- 🔤 loan_created
  > `author_links_2.loan_created` · `varchar` · obligatoria
- 🧩 role_status_3
  > `author_links_2.role_status_3` · `jsonb` · obligatoria
- 🧩 book_started
  > `author_links_2.book_started` · `jsonb` · obligatoria
- 🧩 role_active_2
  > `author_links_2.role_active_2` · `jsonb` · obligatoria
- 🕒 order_reviewed
  > `author_links_2.order_reviewed` · `timestamp` · opcional
- 🔤 card_reviewed
  > `author_links_2.card_reviewed` · `varchar` · opcional
- ☑️ event_position_2
  > `author_links_2.event_position_2` · `bool` · opcional
- 🔢 review_label_2
  > `author_links_2.review_label_2` · `int4` · opcional
- 🔤 role_format
  > `author_links_2.role_format` · `varchar` · obligatoria
- 🕒 payment_region_3
  > `author_links_2.payment_region_3` · `timestamp` · obligatoria
- 🕒 ebook_position
  > `author_links_2.ebook_position` · `timestamp` · obligatoria
- 🧩 tag_archived_2
  > `author_links_2.tag_archived_2` · `jsonb` · obligatoria
- 🧩 shelf_external
  > `author_links_2.shelf_external` · `jsonb` · obligatoria
- 🔢 report_region
  > `author_links_2.report_region` · `int4` · opcional
- ☑️ vendor_label
  > `author_links_2.vendor_label` · `bool` · obligatoria

### shelf_queues
<!-- orbit
id: "shelf_queues"
icon: "📇"
collapsed: true
-->
> `shelf_queues` · tabla de Eventos
>
> Una fila por estante, con 21 columnas y unas 806 mil filas.
>
> **Pertenece a** [`visit_queues`](#visit_queues) · [`book_items`](#book_items)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `shelf_queues.id` · `int8` · obligatoria
- ☑️ loan_ended
  > `shelf_queues.loan_ended` · `bool` · opcional
- 🕒 event_active
  > `shelf_queues.event_active` · `timestamp` · opcional
- 🔗 report_id <!-- orbit link: "#report_logs", status: "clave foránea" -->
  > `shelf_queues.report_id` · `int8` · obligatoria
  >
  > Apunta a [`report_logs`](#report_logs).
- 🔗 donation_id <!-- orbit link: "#donation_queues", status: "clave foránea" -->
  > `shelf_queues.donation_id` · `int8` · obligatoria
  >
  > Apunta a [`donation_queues`](#donation_queues).
- 🕒 copy_code
  > `shelf_queues.copy_code` · `timestamp` · obligatoria
- 🔗 report_id_2 <!-- orbit link: "#report_queues", status: "clave foránea" -->
  > `shelf_queues.report_id_2` · `int8` · obligatoria
  >
  > Apunta a [`report_queues`](#report_queues).
- 🔢 series_reviewed
  > `shelf_queues.series_reviewed` · `int4` · obligatoria
- ☑️ donation_active
  > `shelf_queues.donation_active` · `bool` · obligatoria
- 🔤 review_kind
  > `shelf_queues.review_kind` · `varchar` · obligatoria
- ☑️ shelf_priority
  > `shelf_queues.shelf_priority` · `bool` · obligatoria
- 🕒 payment_status
  > `shelf_queues.payment_status` · `timestamp` · opcional
- ☑️ order_language
  > `shelf_queues.order_language` · `bool` · obligatoria
- 🔗 staff_id <!-- orbit link: "#staff_counts", status: "clave foránea" -->
  > `shelf_queues.staff_id` · `int8` · obligatoria
  >
  > Apunta a [`staff_counts`](#staff_counts).
- 🔗 loan_id <!-- orbit link: "#loan_settings", status: "clave foránea" -->
  > `shelf_queues.loan_id` · `int8` · obligatoria
  >
  > Apunta a [`loan_settings`](#loan_settings).
- 🧩 member_external
  > `shelf_queues.member_external` · `jsonb` · opcional
- 🔤 event_name
  > `shelf_queues.event_name` · `varchar` · obligatoria
- 🔗 fee_id <!-- orbit link: "#fee_requests", status: "clave foránea" -->
  > `shelf_queues.fee_id` · `int8` · obligatoria
  >
  > Apunta a [`fee_requests`](#fee_requests).
- 🧩 hold_name
  > `shelf_queues.hold_name` · `jsonb` · opcional
- 🔤 copy_started
  > `shelf_queues.copy_started` · `varchar` · opcional
- 🔗 copy_id <!-- orbit link: "#copy_items", status: "clave foránea" -->
  > `shelf_queues.copy_id` · `int8` · obligatoria
  >
  > Apunta a [`copy_items`](#copy_items).

### fee_requests
<!-- orbit
id: "fee_requests"
icon: "📗"
collapsed: true
-->
> `fee_requests` · tabla de Eventos
>
> Una fila por multa, con 23 columnas y unas 202 mil filas.
>
> **Pertenece a** [`loan_counts`](#loan_counts) · [`review_queues`](#review_queues)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `fee_requests.id` · `int8` · obligatoria
- 🔢 tag_external
  > `fee_requests.tag_external` · `int4` · obligatoria
- 🔗 license_id <!-- orbit link: "#license_queues", status: "clave foránea" -->
  > `fee_requests.license_id` · `int8` · obligatoria
  >
  > Apunta a [`license_queues`](#license_queues).
- 🔤 license_state
  > `fee_requests.license_state` · `varchar` · obligatoria
- 🧩 hold_active
  > `fee_requests.hold_active` · `jsonb` · obligatoria
- ☑️ event_archived
  > `fee_requests.event_archived` · `bool` · obligatoria
- 🔢 room_title
  > `fee_requests.room_title` · `int4` · obligatoria
- 🕒 payment_position
  > `fee_requests.payment_position` · `timestamp` · opcional
- 🕒 repair_code
  > `fee_requests.repair_code` · `timestamp` · opcional
- 🔗 payment_id <!-- orbit link: "#payment_settings", status: "clave foránea" -->
  > `fee_requests.payment_id` · `int8` · obligatoria
  >
  > Apunta a [`payment_settings`](#payment_settings).
- 🧩 donation_label
  > `fee_requests.donation_label` · `jsonb` · obligatoria
- 🧩 loan_public
  > `fee_requests.loan_public` · `jsonb` · opcional
- 🕒 payment_public
  > `fee_requests.payment_public` · `timestamp` · obligatoria
- 🕒 shelf_count
  > `fee_requests.shelf_count` · `timestamp` · opcional
- 🔗 series_id <!-- orbit link: "#series_history", status: "clave foránea" -->
  > `fee_requests.series_id` · `int8` · obligatoria
  >
  > Apunta a [`series_history`](#series_history).
- ☑️ tag_priority
  > `fee_requests.tag_priority` · `bool` · opcional
- ☑️ report_region
  > `fee_requests.report_region` · `bool` · obligatoria
- 🔗 vendor_id <!-- orbit link: "#vendor_items", status: "clave foránea" -->
  > `fee_requests.vendor_id` · `int8` · obligatoria
  >
  > Apunta a [`vendor_items`](#vendor_items).
- ☑️ repair_region
  > `fee_requests.repair_region` · `bool` · opcional
- 🔢 card_notes
  > `fee_requests.card_notes` · `int4` · obligatoria
- 🔤 license_status
  > `fee_requests.license_status` · `varchar` · opcional
- 🔗 repair_id <!-- orbit link: "#repair_requests", status: "clave foránea" -->
  > `fee_requests.repair_id` · `int8` · obligatoria
  >
  > Apunta a [`repair_requests`](#repair_requests).
- 🧩 room_active
  > `fee_requests.room_active` · `jsonb` · opcional

### event_history
<!-- orbit
id: "event_history"
icon: "📇"
collapsed: true
-->
> `event_history` · tabla de Eventos
>
> Una fila por evento, con 21 columnas y unas 561 mil filas.
>
> **Pertenece a** [`card_links_2`](#card_links_2)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `event_history.id` · `int8` · obligatoria
- 🕒 device_title
  > `event_history.device_title` · `timestamp` · opcional
- ☑️ author_kind
  > `event_history.author_kind` · `bool` · opcional
- ☑️ review_reviewed
  > `event_history.review_reviewed` · `bool` · obligatoria
- 🕒 author_status
  > `event_history.author_status` · `timestamp` · opcional
- 🧩 ebook_amount
  > `event_history.ebook_amount` · `jsonb` · obligatoria
- 🔢 notice_format
  > `event_history.notice_format` · `int4` · obligatoria
- ☑️ branch_reviewed
  > `event_history.branch_reviewed` · `bool` · opcional
- 🧩 role_reviewed
  > `event_history.role_reviewed` · `jsonb` · obligatoria
- 🔤 role_external
  > `event_history.role_external` · `varchar` · obligatoria
- 🔢 branch_label
  > `event_history.branch_label` · `int4` · obligatoria
- 🔗 series_id <!-- orbit link: "#series_history", status: "clave foránea" -->
  > `event_history.series_id` · `int8` · obligatoria
  >
  > Apunta a [`series_history`](#series_history).
- 🔗 copy_id <!-- orbit link: "#copy_items", status: "clave foránea" -->
  > `event_history.copy_id` · `int8` · obligatoria
  >
  > Apunta a [`copy_items`](#copy_items).
- 🔗 shelfs_id <!-- orbit link: "#shelfs", status: "clave foránea" -->
  > `event_history.shelfs_id` · `int8` · obligatoria
  >
  > Apunta a [`shelfs`](#shelfs).
- 🔢 notice_source
  > `event_history.notice_source` · `int4` · obligatoria
- 🔤 book_source
  > `event_history.book_source` · `varchar` · opcional
- 🧩 repair_position
  > `event_history.repair_position` · `jsonb` · obligatoria
- 🔢 payment_priority
  > `event_history.payment_priority` · `int4` · opcional
- 🔤 copy_language
  > `event_history.copy_language` · `varchar` · opcional
- 🕒 staff_format
  > `event_history.staff_format` · `timestamp` · opcional
- 🔤 audit_external
  > `event_history.audit_external` · `varchar` · obligatoria

### audit_links_3
<!-- orbit
id: "audit_links_3"
icon: "🧮"
collapsed: true
-->
> `audit_links_3` · tabla de Eventos
>
> Una fila por auditoría, con 13 columnas y unas 462 mil filas.
>
> **Pertenece a** [`holds`](#holds) · [`report_queues_2`](#report_queues_2) · [`visit_items`](#visit_items)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `audit_links_3.id` · `int8` · obligatoria
- 🧩 book_source
  > `audit_links_3.book_source` · `jsonb` · obligatoria
- ☑️ card_kind
  > `audit_links_3.card_kind` · `bool` · obligatoria
- 🔢 loan_external
  > `audit_links_3.loan_external` · `int4` · opcional
- 🔢 author_title
  > `audit_links_3.author_title` · `int4` · obligatoria
- 🕒 vendor_amount
  > `audit_links_3.vendor_amount` · `timestamp` · opcional
- ☑️ event_title
  > `audit_links_3.event_title` · `bool` · obligatoria
- ☑️ fee_source
  > `audit_links_3.fee_source` · `bool` · obligatoria
- 🔗 ebook_id <!-- orbit link: "#ebook_queues", status: "clave foránea" -->
  > `audit_links_3.ebook_id` · `int8` · obligatoria
  >
  > Apunta a [`ebook_queues`](#ebook_queues).
- 🕒 review_kind
  > `audit_links_3.review_kind` · `timestamp` · obligatoria
- 🧩 shelf_active
  > `audit_links_3.shelf_active` · `jsonb` · obligatoria
- 🕒 hold_code
  > `audit_links_3.hold_code` · `timestamp` · opcional
- 🔢 staff_ended
  > `audit_links_3.staff_ended` · `int4` · opcional

### report_logs_2
<!-- orbit
id: "report_logs_2"
icon: "📗"
collapsed: true
-->
> `report_logs_2` · tabla de Eventos
>
> Una fila por informe, con 22 columnas y unas 284 mil filas.
>
> **Pertenece a** [`ebook_requests`](#ebook_requests) · [`staff_settings`](#staff_settings) · [`loan_history`](#loan_history) · [`author_links_2`](#author_links_2)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `report_logs_2.id` · `int8` · obligatoria
- 🕒 book_kind
  > `report_logs_2.book_kind` · `timestamp` · opcional
- 🔗 visit_id <!-- orbit link: "#visit_links", status: "clave foránea" -->
  > `report_logs_2.visit_id` · `int8` · obligatoria
  >
  > Apunta a [`visit_links`](#visit_links).
- 🕒 review_label
  > `report_logs_2.review_label` · `timestamp` · obligatoria
- 🧩 loan_active
  > `report_logs_2.loan_active` · `jsonb` · opcional
- 🔗 author_id <!-- orbit link: "#author_links_2", status: "clave foránea" -->
  > `report_logs_2.author_id` · `int8` · obligatoria
  >
  > Apunta a [`author_links_2`](#author_links_2).
- 🔤 fee_public
  > `report_logs_2.fee_public` · `varchar` · obligatoria
- 🔗 branchs_id <!-- orbit link: "#branchs", status: "clave foránea" -->
  > `report_logs_2.branchs_id` · `int8` · obligatoria
  >
  > Apunta a [`branchs`](#branchs).
- 🧩 ebook_position
  > `report_logs_2.ebook_position` · `jsonb` · obligatoria
- ☑️ review_started
  > `report_logs_2.review_started` · `bool` · obligatoria
- 🧩 hold_format
  > `report_logs_2.hold_format` · `jsonb` · obligatoria
- 🔢 budget_region
  > `report_logs_2.budget_region` · `int4` · opcional
- 🔢 staff_name
  > `report_logs_2.staff_name` · `int4` · obligatoria
- 🕒 branch_ended
  > `report_logs_2.branch_ended` · `timestamp` · opcional
- 🔢 payment_archived
  > `report_logs_2.payment_archived` · `int4` · obligatoria
- 🕒 fee_reviewed
  > `report_logs_2.fee_reviewed` · `timestamp` · opcional
- 🕒 branch_status
  > `report_logs_2.branch_status` · `timestamp` · obligatoria
- 🕒 payment_amount
  > `report_logs_2.payment_amount` · `timestamp` · obligatoria
- ☑️ branch_public
  > `report_logs_2.branch_public` · `bool` · obligatoria
- 🔗 shelf_id <!-- orbit link: "#shelf_counts", status: "clave foránea" -->
  > `report_logs_2.shelf_id` · `int8` · obligatoria
  >
  > Apunta a [`shelf_counts`](#shelf_counts).
- ☑️ device_total
  > `report_logs_2.device_total` · `bool` · obligatoria
- 🔗 vendor_id <!-- orbit link: "#vendor_items", status: "clave foránea" -->
  > `report_logs_2.vendor_id` · `int8` · obligatoria
  >
  > Apunta a [`vendor_items`](#vendor_items).

### audit_queues
<!-- orbit
id: "audit_queues"
icon: "🗃️"
collapsed: true
-->
> `audit_queues` · tabla de Eventos
>
> Una fila por auditoría, con 14 columnas y unas 478 mil filas.
>
> **Pertenece a** [`report_items`](#report_items) · [`notice_counts_2`](#notice_counts_2) · [`loan_history`](#loan_history)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `audit_queues.id` · `int8` · obligatoria
- 🕒 branch_name
  > `audit_queues.branch_name` · `timestamp` · obligatoria
- 🔗 loan_id <!-- orbit link: "#loan_counts", status: "clave foránea" -->
  > `audit_queues.loan_id` · `int8` · obligatoria
  >
  > Apunta a [`loan_counts`](#loan_counts).
- 🔗 budget_id <!-- orbit link: "#budget_queues", status: "clave foránea" -->
  > `audit_queues.budget_id` · `int8` · obligatoria
  >
  > Apunta a [`budget_queues`](#budget_queues).
- 🧩 device_code
  > `audit_queues.device_code` · `jsonb` · obligatoria
- ☑️ fee_amount
  > `audit_queues.fee_amount` · `bool` · opcional
- ☑️ author_code
  > `audit_queues.author_code` · `bool` · opcional
- 🔢 notice_public
  > `audit_queues.notice_public` · `int4` · opcional
- ☑️ staff_started
  > `audit_queues.staff_started` · `bool` · obligatoria
- 🕒 card_source
  > `audit_queues.card_source` · `timestamp` · obligatoria
- 🧩 role_created
  > `audit_queues.role_created` · `jsonb` · obligatoria
- 🧩 notice_amount
  > `audit_queues.notice_amount` · `jsonb` · opcional
- 🔗 event_id <!-- orbit link: "#event_queues", status: "clave foránea" -->
  > `audit_queues.event_id` · `int8` · obligatoria
  >
  > Apunta a [`event_queues`](#event_queues).
- 🔢 branch_count
  > `audit_queues.branch_count` · `int4` · obligatoria

### ebook_versions
<!-- orbit
id: "ebook_versions"
icon: "🧮"
collapsed: true
-->
> `ebook_versions` · tabla de Eventos
>
> Una fila por libro electrónico, con 15 columnas y unas 874 mil filas.
>
> **Pertenece a** [`visit_items`](#visit_items) · [`order_versions`](#order_versions) · [`device_settings`](#device_settings)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `ebook_versions.id` · `int8` · obligatoria
- 🔢 series_external
  > `ebook_versions.series_external` · `int4` · obligatoria
- ☑️ review_priority
  > `ebook_versions.review_priority` · `bool` · obligatoria
- ☑️ role_updated
  > `ebook_versions.role_updated` · `bool` · opcional
- 🔢 vendor_priority
  > `ebook_versions.vendor_priority` · `int4` · obligatoria
- 🕒 copy_state
  > `ebook_versions.copy_state` · `timestamp` · opcional
- ☑️ audit_amount
  > `ebook_versions.audit_amount` · `bool` · opcional
- 🔗 staff_id <!-- orbit link: "#staff_counts", status: "clave foránea" -->
  > `ebook_versions.staff_id` · `int8` · obligatoria
  >
  > Apunta a [`staff_counts`](#staff_counts).
- 🔗 loan_id <!-- orbit link: "#loan_logs_2", status: "clave foránea" -->
  > `ebook_versions.loan_id` · `int8` · obligatoria
  >
  > Apunta a [`loan_logs_2`](#loan_logs_2).
- 🧩 vendor_label
  > `ebook_versions.vendor_label` · `jsonb` · opcional
- 🕒 donation_amount
  > `ebook_versions.donation_amount` · `timestamp` · opcional
- 🔗 book_id <!-- orbit link: "#book_items", status: "clave foránea" -->
  > `ebook_versions.book_id` · `int8` · obligatoria
  >
  > Apunta a [`book_items`](#book_items).
- 🔢 payment_kind
  > `ebook_versions.payment_kind` · `int4` · opcional
- 🔢 role_status
  > `ebook_versions.role_status` · `int4` · opcional
- 🧩 review_state
  > `ebook_versions.review_state` · `jsonb` · opcional

### loan_settings
<!-- orbit
id: "loan_settings"
icon: "🧷"
collapsed: true
-->
> `loan_settings` · tabla de Eventos
>
> Una fila por préstamo, con 12 columnas y unas 314 mil filas.
>
> **Pertenece a** [`role_counts`](#role_counts)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `loan_settings.id` · `int8` · obligatoria
- 🕒 card_name
  > `loan_settings.card_name` · `timestamp` · obligatoria
- 🔗 order_id <!-- orbit link: "#order_logs", status: "clave foránea" -->
  > `loan_settings.order_id` · `int8` · obligatoria
  >
  > Apunta a [`order_logs`](#order_logs).
- 🧩 staff_notes
  > `loan_settings.staff_notes` · `jsonb` · opcional
- ☑️ license_started
  > `loan_settings.license_started` · `bool` · obligatoria
- 🕒 shelf_total
  > `loan_settings.shelf_total` · `timestamp` · obligatoria
- 🔢 book_amount
  > `loan_settings.book_amount` · `int4` · obligatoria
- ☑️ staff_region
  > `loan_settings.staff_region` · `bool` · obligatoria
- 🧩 review_ended
  > `loan_settings.review_ended` · `jsonb` · obligatoria
- 🧩 fee_reviewed
  > `loan_settings.fee_reviewed` · `jsonb` · obligatoria
- 🔗 visit_id <!-- orbit link: "#visit_links", status: "clave foránea" -->
  > `loan_settings.visit_id` · `int8` · obligatoria
  >
  > Apunta a [`visit_links`](#visit_links).
- 🕒 copy_region
  > `loan_settings.copy_region` · `timestamp` · opcional

### tag_requests
<!-- orbit
id: "tag_requests"
icon: "🧮"
collapsed: true
-->
> `tag_requests` · tabla de Eventos
>
> Una fila por etiqueta, con 10 columnas y unas 466 mil filas.
>
> **Pertenece a** [`staff_settings`](#staff_settings) · [`author_links`](#author_links) · [`budget_settings_2`](#budget_settings_2) · [`ebook_queues`](#ebook_queues)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `tag_requests.id` · `int8` · obligatoria
- 🔤 license_archived
  > `tag_requests.license_archived` · `varchar` · obligatoria
- 🔤 order_state
  > `tag_requests.order_state` · `varchar` · obligatoria
- ☑️ tag_label
  > `tag_requests.tag_label` · `bool` · opcional
- 🕒 device_language
  > `tag_requests.device_language` · `timestamp` · opcional
- 🕒 book_started
  > `tag_requests.book_started` · `timestamp` · obligatoria
- 🔢 event_priority
  > `tag_requests.event_priority` · `int4` · obligatoria
- ☑️ vendor_total
  > `tag_requests.vendor_total` · `bool` · opcional
- 🔢 card_count
  > `tag_requests.card_count` · `int4` · opcional
- 🧩 role_notes
  > `tag_requests.role_notes` · `jsonb` · obligatoria

## 💳 Multas y pagos
<!-- orbit
id: "area-fees-payments"
color: "#D97706"
-->
> 13 tablas. Las más grandes: [`license_versions`](#license_versions), [`member_counts_2`](#member_counts_2), [`review_versions`](#review_versions).
>
> **Relacionada con** [`license_versions`](#license_versions) · [`room_logs`](#room_logs) · [`audit_links`](#audit_links)

### license_versions
<!-- orbit
id: "license_versions"
icon: "📗"
collapsed: true
-->
> `license_versions` · tabla de Multas y pagos
>
> Una fila por licencia, con 10 columnas y unas 440 mil filas.
>
> **Pertenece a** [`review_logs`](#review_logs) · [`staff_counts`](#staff_counts)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `license_versions.id` · `int8` · obligatoria
- 🔢 card_public
  > `license_versions.card_public` · `int4` · obligatoria
- 🔗 holds_id <!-- orbit link: "#holds_2", status: "clave foránea" -->
  > `license_versions.holds_id` · `int8` · obligatoria
  >
  > Apunta a [`holds_2`](#holds_2).
- 🔢 visit_archived
  > `license_versions.visit_archived` · `int4` · obligatoria
- 🔢 copy_title
  > `license_versions.copy_title` · `int4` · obligatoria
- ☑️ room_language
  > `license_versions.room_language` · `bool` · obligatoria
- 🔗 license_id <!-- orbit link: "#license_logs_2", status: "clave foránea" -->
  > `license_versions.license_id` · `int8` · obligatoria
  >
  > Apunta a [`license_logs_2`](#license_logs_2).
- 🧩 series_priority
  > `license_versions.series_priority` · `jsonb` · obligatoria
- 🔢 event_started
  > `license_versions.event_started` · `int4` · obligatoria
- 🔤 shelf_amount
  > `license_versions.shelf_amount` · `varchar` · opcional

### member_counts_2
<!-- orbit
id: "member_counts_2"
icon: "📇"
collapsed: true
-->
> `member_counts_2` · tabla de Multas y pagos
>
> Una fila por socio, con 23 columnas y unas 525 mil filas.
>
> **Pertenece a** [`hold_history`](#hold_history) · [`fee_history`](#fee_history)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `member_counts_2.id` · `int8` · obligatoria
- 🕒 donation_archived
  > `member_counts_2.donation_archived` · `timestamp` · obligatoria
- 🔤 author_external
  > `member_counts_2.author_external` · `varchar` · opcional
- 🕒 review_count
  > `member_counts_2.review_count` · `timestamp` · obligatoria
- 🔤 book_code
  > `member_counts_2.book_code` · `varchar` · obligatoria
- 🧩 order_kind
  > `member_counts_2.order_kind` · `jsonb` · obligatoria
- 🔤 vendor_external
  > `member_counts_2.vendor_external` · `varchar` · opcional
- 🕒 shelf_reviewed
  > `member_counts_2.shelf_reviewed` · `timestamp` · opcional
- 🔗 device_id <!-- orbit link: "#device_items", status: "clave foránea" -->
  > `member_counts_2.device_id` · `int8` · obligatoria
  >
  > Apunta a [`device_items`](#device_items).
- ☑️ order_started
  > `member_counts_2.order_started` · `bool` · obligatoria
- 🔗 ebook_id <!-- orbit link: "#ebook_queues", status: "clave foránea" -->
  > `member_counts_2.ebook_id` · `int8` · obligatoria
  >
  > Apunta a [`ebook_queues`](#ebook_queues).
- 🔤 vendor_code
  > `member_counts_2.vendor_code` · `varchar` · obligatoria
- ☑️ audit_amount
  > `member_counts_2.audit_amount` · `bool` · opcional
- 🔗 staff_id <!-- orbit link: "#staff_logs", status: "clave foránea" -->
  > `member_counts_2.staff_id` · `int8` · obligatoria
  >
  > Apunta a [`staff_logs`](#staff_logs).
- 🔗 loan_id <!-- orbit link: "#loan_counts", status: "clave foránea" -->
  > `member_counts_2.loan_id` · `int8` · obligatoria
  >
  > Apunta a [`loan_counts`](#loan_counts).
- 🔢 order_created
  > `member_counts_2.order_created` · `int4` · obligatoria
- 🧩 device_amount
  > `member_counts_2.device_amount` · `jsonb` · obligatoria
- ☑️ order_kind_2
  > `member_counts_2.order_kind_2` · `bool` · obligatoria
- 🔗 seriess_id <!-- orbit link: "#seriess", status: "clave foránea" -->
  > `member_counts_2.seriess_id` · `int8` · obligatoria
  >
  > Apunta a [`seriess`](#seriess).
- 🔢 fee_count
  > `member_counts_2.fee_count` · `int4` · obligatoria
- 🧩 book_name
  > `member_counts_2.book_name` · `jsonb` · obligatoria
- 🕒 budget_format
  > `member_counts_2.budget_format` · `timestamp` · obligatoria
- 🔤 audit_title
  > `member_counts_2.audit_title` · `varchar` · opcional

### review_versions
<!-- orbit
id: "review_versions"
icon: "📗"
collapsed: true
-->
> `review_versions` · tabla de Multas y pagos
>
> Una fila por reseña, con 8 columnas y unas 753 mil filas.
>
> **Pertenece a** [`audit_links_4`](#audit_links_4)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `review_versions.id` · `int8` · obligatoria
- ☑️ copy_started
  > `review_versions.copy_started` · `bool` · obligatoria
- 🧩 event_name
  > `review_versions.event_name` · `jsonb` · obligatoria
- 🔗 license_id <!-- orbit link: "#license_logs_2", status: "clave foránea" -->
  > `review_versions.license_id` · `int8` · obligatoria
  >
  > Apunta a [`license_logs_2`](#license_logs_2).
- 🧩 order_priority
  > `review_versions.order_priority` · `jsonb` · opcional
- 🧩 event_language
  > `review_versions.event_language` · `jsonb` · obligatoria
- 🔗 report_id <!-- orbit link: "#report_links", status: "clave foránea" -->
  > `review_versions.report_id` · `int8` · obligatoria
  >
  > Apunta a [`report_links`](#report_links).
- 🔢 audit_language
  > `review_versions.audit_language` · `int4` · obligatoria

### author_links_3
<!-- orbit
id: "author_links_3"
icon: "📇"
collapsed: true
-->
> `author_links_3` · tabla de Multas y pagos
>
> Una fila por autor, con 12 columnas y unas 688 mil filas.
>
> **Pertenece a** [`room_logs`](#room_logs) · [`report_logs_2`](#report_logs_2)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `author_links_3.id` · `int8` · obligatoria
- 🕒 budget_started
  > `author_links_3.budget_started` · `timestamp` · opcional
- 🔤 license_count
  > `author_links_3.license_count` · `varchar` · obligatoria
- 🔗 visit_id <!-- orbit link: "#visit_items", status: "clave foránea" -->
  > `author_links_3.visit_id` · `int8` · obligatoria
  >
  > Apunta a [`visit_items`](#visit_items).
- 🧩 branch_total
  > `author_links_3.branch_total` · `jsonb` · obligatoria
- ☑️ vendor_archived
  > `author_links_3.vendor_archived` · `bool` · opcional
- 🧩 branch_region
  > `author_links_3.branch_region` · `jsonb` · opcional
- 🔤 series_amount
  > `author_links_3.series_amount` · `varchar` · obligatoria
- 🧩 series_created
  > `author_links_3.series_created` · `jsonb` · opcional
- 🔗 donation_id <!-- orbit link: "#donation_history", status: "clave foránea" -->
  > `author_links_3.donation_id` · `int8` · obligatoria
  >
  > Apunta a [`donation_history`](#donation_history).
- ☑️ shelf_region
  > `author_links_3.shelf_region` · `bool` · obligatoria
- 🕒 fee_label
  > `author_links_3.fee_label` · `timestamp` · obligatoria

### device_settings
<!-- orbit
id: "device_settings"
icon: "📗"
collapsed: true
-->
> `device_settings` · tabla de Multas y pagos
>
> Una fila por dispositivo, con 19 columnas y unas 218 mil filas.
>
> **Pertenece a** [`ebook_queues`](#ebook_queues) · [`license_settings`](#license_settings) · [`author_links_3`](#author_links_3)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `device_settings.id` · `int8` · obligatoria
- 🔤 repair_notes
  > `device_settings.repair_notes` · `varchar` · obligatoria
- 🔢 tag_started
  > `device_settings.tag_started` · `int4` · opcional
- ☑️ role_external
  > `device_settings.role_external` · `bool` · opcional
- 🧩 branch_reviewed
  > `device_settings.branch_reviewed` · `jsonb` · opcional
- 🔢 loan_state
  > `device_settings.loan_state` · `int4` · obligatoria
- 🔢 tag_updated
  > `device_settings.tag_updated` · `int4` · obligatoria
- 🔤 branch_source
  > `device_settings.branch_source` · `varchar` · opcional
- 🔢 visit_kind
  > `device_settings.visit_kind` · `int4` · opcional
- 🔤 shelf_started
  > `device_settings.shelf_started` · `varchar` · opcional
- 🔤 payment_reviewed
  > `device_settings.payment_reviewed` · `varchar` · opcional
- 🔢 device_external
  > `device_settings.device_external` · `int4` · obligatoria
- 🧩 event_priority
  > `device_settings.event_priority` · `jsonb` · obligatoria
- 🧩 role_position
  > `device_settings.role_position` · `jsonb` · obligatoria
- 🔤 book_created
  > `device_settings.book_created` · `varchar` · opcional
- 🔗 payment_id <!-- orbit link: "#payment_requests", status: "clave foránea" -->
  > `device_settings.payment_id` · `int8` · obligatoria
  >
  > Apunta a [`payment_requests`](#payment_requests).
- 🧩 branch_count
  > `device_settings.branch_count` · `jsonb` · obligatoria
- 🔢 author_state
  > `device_settings.author_state` · `int4` · obligatoria
- 🔗 book_id <!-- orbit link: "#book_items", status: "clave foránea" -->
  > `device_settings.book_id` · `int8` · obligatoria
  >
  > Apunta a [`book_items`](#book_items).

### staff_logs
<!-- orbit
id: "staff_logs"
icon: "🧮"
collapsed: true
-->
> `staff_logs` · tabla de Multas y pagos
>
> Una fila por empleado, con 21 columnas y unas 318 mil filas.
>
> **Pertenece a** [`device_items`](#device_items) · [`author_history`](#author_history) · [`report_logs`](#report_logs) · [`member_counts`](#member_counts)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `staff_logs.id` · `int8` · obligatoria
- ☑️ shelf_reviewed
  > `staff_logs.shelf_reviewed` · `bool` · obligatoria
- 🔤 member_count
  > `staff_logs.member_count` · `varchar` · opcional
- 🔗 staff_id <!-- orbit link: "#staff_counts", status: "clave foránea" -->
  > `staff_logs.staff_id` · `int8` · obligatoria
  >
  > Apunta a [`staff_counts`](#staff_counts).
- 🧩 device_language
  > `staff_logs.device_language` · `jsonb` · obligatoria
- 🕒 ebook_amount
  > `staff_logs.ebook_amount` · `timestamp` · obligatoria
- 🔢 license_name
  > `staff_logs.license_name` · `int4` · obligatoria
- 🔗 vendor_id <!-- orbit link: "#vendor_counts", status: "clave foránea" -->
  > `staff_logs.vendor_id` · `int8` · obligatoria
  >
  > Apunta a [`vendor_counts`](#vendor_counts).
- 🔤 role_public
  > `staff_logs.role_public` · `varchar` · obligatoria
- 🔗 loan_id <!-- orbit link: "#loan_logs_2", status: "clave foránea" -->
  > `staff_logs.loan_id` · `int8` · obligatoria
  >
  > Apunta a [`loan_logs_2`](#loan_logs_2).
- ☑️ branch_kind
  > `staff_logs.branch_kind` · `bool` · obligatoria
- 🔢 license_format
  > `staff_logs.license_format` · `int4` · opcional
- 🔢 budget_title
  > `staff_logs.budget_title` · `int4` · opcional
- 🔗 event_id <!-- orbit link: "#event_queues", status: "clave foránea" -->
  > `staff_logs.event_id` · `int8` · obligatoria
  >
  > Apunta a [`event_queues`](#event_queues).
- ☑️ loan_started
  > `staff_logs.loan_started` · `bool` · obligatoria
- ☑️ donation_active
  > `staff_logs.donation_active` · `bool` · opcional
- ☑️ notice_label
  > `staff_logs.notice_label` · `bool` · opcional
- 🔗 series_id <!-- orbit link: "#series_counts_2", status: "clave foránea" -->
  > `staff_logs.series_id` · `int8` · obligatoria
  >
  > Apunta a [`series_counts_2`](#series_counts_2).
- 🕒 vendor_started
  > `staff_logs.vendor_started` · `timestamp` · opcional
- 🔗 tag_id <!-- orbit link: "#tag_settings", status: "clave foránea" -->
  > `staff_logs.tag_id` · `int8` · obligatoria
  >
  > Apunta a [`tag_settings`](#tag_settings).
- 🔤 vendor_active
  > `staff_logs.vendor_active` · `varchar` · obligatoria

### loan_counts
<!-- orbit
id: "loan_counts"
icon: "📗"
collapsed: true
-->
> `loan_counts` · tabla de Multas y pagos
>
> Una fila por préstamo, con 13 columnas y unas 672 mil filas.
>
> **Pertenece a** [`role_counts`](#role_counts) · [`license_queues`](#license_queues) · [`device_settings`](#device_settings)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `loan_counts.id` · `int8` · obligatoria
- 🔗 notice_id <!-- orbit link: "#notice_counts", status: "clave foránea" -->
  > `loan_counts.notice_id` · `int8` · obligatoria
  >
  > Apunta a [`notice_counts`](#notice_counts).
- 🧩 series_code
  > `loan_counts.series_code` · `jsonb` · opcional
- ☑️ role_ended
  > `loan_counts.role_ended` · `bool` · obligatoria
- 🔤 shelf_updated
  > `loan_counts.shelf_updated` · `varchar` · obligatoria
- 🔤 branch_public
  > `loan_counts.branch_public` · `varchar` · obligatoria
- 🔢 fee_priority
  > `loan_counts.fee_priority` · `int4` · opcional
- 🕒 event_external
  > `loan_counts.event_external` · `timestamp` · opcional
- ☑️ room_code
  > `loan_counts.room_code` · `bool` · obligatoria
- 🔤 license_position
  > `loan_counts.license_position` · `varchar` · opcional
- 🕒 staff_label
  > `loan_counts.staff_label` · `timestamp` · obligatoria
- ☑️ vendor_kind
  > `loan_counts.vendor_kind` · `bool` · opcional
- ☑️ author_position
  > `loan_counts.author_position` · `bool` · opcional

### report_queues_2
<!-- orbit
id: "report_queues_2"
icon: "🧷"
collapsed: true
-->
> `report_queues_2` · tabla de Multas y pagos
>
> Una fila por informe, con 24 columnas y unas 666 mil filas.
>
> **Pertenece a** [`vendor_counts`](#vendor_counts) · [`copy_versions`](#copy_versions) · [`ebook_logs`](#ebook_logs)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `report_queues_2.id` · `int8` · obligatoria
- ☑️ repair_archived
  > `report_queues_2.repair_archived` · `bool` · opcional
- 🧩 donation_amount
  > `report_queues_2.donation_amount` · `jsonb` · opcional
- 🕒 hold_source
  > `report_queues_2.hold_source` · `timestamp` · obligatoria
- 🔗 tags_id <!-- orbit link: "#tags", status: "clave foránea" -->
  > `report_queues_2.tags_id` · `int8` · obligatoria
  >
  > Apunta a [`tags`](#tags).
- 🔗 vendor_id <!-- orbit link: "#vendor_history", status: "clave foránea" -->
  > `report_queues_2.vendor_id` · `int8` · obligatoria
  >
  > Apunta a [`vendor_history`](#vendor_history).
- 🔤 repair_state
  > `report_queues_2.repair_state` · `varchar` · opcional
- 🧩 payment_ended
  > `report_queues_2.payment_ended` · `jsonb` · obligatoria
- 🕒 repair_kind
  > `report_queues_2.repair_kind` · `timestamp` · obligatoria
- 🕒 member_status
  > `report_queues_2.member_status` · `timestamp` · obligatoria
- 🔢 repair_name
  > `report_queues_2.repair_name` · `int4` · opcional
- 🔗 series_id <!-- orbit link: "#series_requests", status: "clave foránea" -->
  > `report_queues_2.series_id` · `int8` · obligatoria
  >
  > Apunta a [`series_requests`](#series_requests).
- 🔗 audit_id <!-- orbit link: "#audit_links_4", status: "clave foránea" -->
  > `report_queues_2.audit_id` · `int8` · obligatoria
  >
  > Apunta a [`audit_links_4`](#audit_links_4).
- 🔢 order_language
  > `report_queues_2.order_language` · `int4` · opcional
- ☑️ shelf_kind
  > `report_queues_2.shelf_kind` · `bool` · opcional
- ☑️ ebook_language
  > `report_queues_2.ebook_language` · `bool` · opcional
- 🔗 budget_id <!-- orbit link: "#budget_queues", status: "clave foránea" -->
  > `report_queues_2.budget_id` · `int8` · obligatoria
  >
  > Apunta a [`budget_queues`](#budget_queues).
- 🕒 order_active
  > `report_queues_2.order_active` · `timestamp` · opcional
- ☑️ shelf_total
  > `report_queues_2.shelf_total` · `bool` · obligatoria
- 🧩 device_state
  > `report_queues_2.device_state` · `jsonb` · obligatoria
- 🔤 budget_public
  > `report_queues_2.budget_public` · `varchar` · obligatoria
- 🔗 copy_id <!-- orbit link: "#copy_links", status: "clave foránea" -->
  > `report_queues_2.copy_id` · `int8` · obligatoria
  >
  > Apunta a [`copy_links`](#copy_links).
- 🔗 hold_id <!-- orbit link: "#hold_links_2", status: "clave foránea" -->
  > `report_queues_2.hold_id` · `int8` · obligatoria
  >
  > Apunta a [`hold_links_2`](#hold_links_2).
- ☑️ budget_created
  > `report_queues_2.budget_created` · `bool` · obligatoria

### card_logs
<!-- orbit
id: "card_logs"
icon: "🗃️"
collapsed: true
-->
> `card_logs` · tabla de Multas y pagos
>
> Una fila por credencial, con 15 columnas y unas 177 mil filas.
>
> **Pertenece a** [`vendor_history`](#vendor_history) · [`budgets`](#budgets) · [`fee_requests`](#fee_requests) · [`shelf_queues`](#shelf_queues)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `card_logs.id` · `int8` · obligatoria
- 🧩 room_priority
  > `card_logs.room_priority` · `jsonb` · opcional
- 🔗 review_id <!-- orbit link: "#review_logs", status: "clave foránea" -->
  > `card_logs.review_id` · `int8` · obligatoria
  >
  > Apunta a [`review_logs`](#review_logs).
- ☑️ report_total
  > `card_logs.report_total` · `bool` · obligatoria
- 🔗 audit_id <!-- orbit link: "#audit_settings", status: "clave foránea" -->
  > `card_logs.audit_id` · `int8` · obligatoria
  >
  > Apunta a [`audit_settings`](#audit_settings).
- 🕒 room_status
  > `card_logs.room_status` · `timestamp` · opcional
- 🧩 repair_updated
  > `card_logs.repair_updated` · `jsonb` · obligatoria
- ☑️ repair_count
  > `card_logs.repair_count` · `bool` · obligatoria
- 🧩 review_source
  > `card_logs.review_source` · `jsonb` · obligatoria
- 🔤 ebook_active
  > `card_logs.ebook_active` · `varchar` · opcional
- ☑️ tag_ended
  > `card_logs.tag_ended` · `bool` · obligatoria
- ☑️ notice_public
  > `card_logs.notice_public` · `bool` · obligatoria
- 🔗 ebook_id <!-- orbit link: "#ebook_versions", status: "clave foránea" -->
  > `card_logs.ebook_id` · `int8` · obligatoria
  >
  > Apunta a [`ebook_versions`](#ebook_versions).
- ☑️ fee_amount
  > `card_logs.fee_amount` · `bool` · opcional
- 🔤 copy_region
  > `card_logs.copy_region` · `varchar` · obligatoria

### donation_queues
<!-- orbit
id: "donation_queues"
icon: "🧮"
collapsed: true
-->
> `donation_queues` · tabla de Multas y pagos
>
> Una fila por donación, con 15 columnas y unas 114 mil filas.
>
> **Pertenece a** [`role_counts`](#role_counts) · [`author_history`](#author_history)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `donation_queues.id` · `int8` · obligatoria
- 🔢 budget_count
  > `donation_queues.budget_count` · `int4` · opcional
- 🕒 license_notes
  > `donation_queues.license_notes` · `timestamp` · opcional
- 🔢 staff_public
  > `donation_queues.staff_public` · `int4` · opcional
- 🔤 book_kind
  > `donation_queues.book_kind` · `varchar` · opcional
- ☑️ room_count
  > `donation_queues.room_count` · `bool` · obligatoria
- 🔢 branch_active
  > `donation_queues.branch_active` · `int4` · opcional
- 🧩 visit_ended
  > `donation_queues.visit_ended` · `jsonb` · obligatoria
- 🕒 loan_state
  > `donation_queues.loan_state` · `timestamp` · opcional
- 🔗 holds_id <!-- orbit link: "#holds", status: "clave foránea" -->
  > `donation_queues.holds_id` · `int8` · obligatoria
  >
  > Apunta a [`holds`](#holds).
- 🔢 donation_source
  > `donation_queues.donation_source` · `int4` · obligatoria
- ☑️ donation_label
  > `donation_queues.donation_label` · `bool` · opcional
- 🔢 ebook_priority
  > `donation_queues.ebook_priority` · `int4` · opcional
- ☑️ donation_title
  > `donation_queues.donation_title` · `bool` · obligatoria
- 🕒 report_amount
  > `donation_queues.report_amount` · `timestamp` · opcional

### author_history
<!-- orbit
id: "author_history"
icon: "🧷"
collapsed: true
-->
> `author_history` · tabla de Multas y pagos
>
> Una fila por autor, con 23 columnas y unas 380 mil filas.
>
> **Pertenece a** [`vendor_history`](#vendor_history)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `author_history.id` · `int8` · obligatoria
- ☑️ notice_language
  > `author_history.notice_language` · `bool` · obligatoria
- 🕒 budget_source
  > `author_history.budget_source` · `timestamp` · obligatoria
- 🕒 event_ended
  > `author_history.event_ended` · `timestamp` · obligatoria
- 🔗 events_id <!-- orbit link: "#events", status: "clave foránea" -->
  > `author_history.events_id` · `int8` · obligatoria
  >
  > Apunta a [`events`](#events).
- 🕒 room_position
  > `author_history.room_position` · `timestamp` · obligatoria
- ☑️ order_total
  > `author_history.order_total` · `bool` · obligatoria
- 🔗 budget_id <!-- orbit link: "#budget_settings_2", status: "clave foránea" -->
  > `author_history.budget_id` · `int8` · obligatoria
  >
  > Apunta a [`budget_settings_2`](#budget_settings_2).
- 🧩 staff_external
  > `author_history.staff_external` · `jsonb` · obligatoria
- 🔗 author_id <!-- orbit link: "#author_links_2", status: "clave foránea" -->
  > `author_history.author_id` · `int8` · obligatoria
  >
  > Apunta a [`author_links_2`](#author_links_2).
- 🔗 book_id <!-- orbit link: "#book_items", status: "clave foránea" -->
  > `author_history.book_id` · `int8` · obligatoria
  >
  > Apunta a [`book_items`](#book_items).
- 🕒 payment_source
  > `author_history.payment_source` · `timestamp` · opcional
- 🧩 order_name
  > `author_history.order_name` · `jsonb` · obligatoria
- 🔢 ebook_active
  > `author_history.ebook_active` · `int4` · obligatoria
- 🧩 card_source
  > `author_history.card_source` · `jsonb` · opcional
- 🔤 vendor_notes
  > `author_history.vendor_notes` · `varchar` · obligatoria
- 🕒 loan_reviewed
  > `author_history.loan_reviewed` · `timestamp` · opcional
- 🔗 branch_id <!-- orbit link: "#branch_links", status: "clave foránea" -->
  > `author_history.branch_id` · `int8` · obligatoria
  >
  > Apunta a [`branch_links`](#branch_links).
- 🔗 tag_id <!-- orbit link: "#tag_settings", status: "clave foránea" -->
  > `author_history.tag_id` · `int8` · obligatoria
  >
  > Apunta a [`tag_settings`](#tag_settings).
- 🕒 visit_reviewed
  > `author_history.visit_reviewed` · `timestamp` · opcional
- 🧩 payment_format
  > `author_history.payment_format` · `jsonb` · opcional
- 🧩 hold_position
  > `author_history.hold_position` · `jsonb` · obligatoria
- 🔗 review_id <!-- orbit link: "#review_queues", status: "clave foránea" -->
  > `author_history.review_id` · `int8` · obligatoria
  >
  > Apunta a [`review_queues`](#review_queues).

### repair_requests
<!-- orbit
id: "repair_requests"
icon: "📇"
collapsed: true
-->
> `repair_requests` · tabla de Multas y pagos
>
> Una fila por reparación, con 23 columnas y unas 93 mil filas.
>
> **Pertenece a** [`copy_links`](#copy_links) · [`notice_counts`](#notice_counts) · [`license_versions`](#license_versions) · [`role_counts`](#role_counts)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `repair_requests.id` · `int8` · obligatoria
- ☑️ donation_label
  > `repair_requests.donation_label` · `bool` · opcional
- 🔗 role_id <!-- orbit link: "#role_counts", status: "clave foránea" -->
  > `repair_requests.role_id` · `int8` · obligatoria
  >
  > Apunta a [`role_counts`](#role_counts).
- 🕒 book_archived
  > `repair_requests.book_archived` · `timestamp` · opcional
- 🔤 tag_source
  > `repair_requests.tag_source` · `varchar` · obligatoria
- 🧩 series_priority
  > `repair_requests.series_priority` · `jsonb` · obligatoria
- 🕒 report_position
  > `repair_requests.report_position` · `timestamp` · opcional
- 🔢 audit_reviewed
  > `repair_requests.audit_reviewed` · `int4` · obligatoria
- ☑️ role_reviewed
  > `repair_requests.role_reviewed` · `bool` · obligatoria
- ☑️ review_amount
  > `repair_requests.review_amount` · `bool` · opcional
- 🕒 staff_position
  > `repair_requests.staff_position` · `timestamp` · obligatoria
- 🔗 repair_id <!-- orbit link: "#repair_requests", status: "clave foránea" -->
  > `repair_requests.repair_id` · `int8` · obligatoria
  >
  > Apunta a [`repair_requests`](#repair_requests).
- 🧩 book_code
  > `repair_requests.book_code` · `jsonb` · opcional
- 🕒 donation_reviewed
  > `repair_requests.donation_reviewed` · `timestamp` · opcional
- ☑️ visit_label
  > `repair_requests.visit_label` · `bool` · obligatoria
- 🔢 donation_created
  > `repair_requests.donation_created` · `int4` · obligatoria
- 🔤 device_language
  > `repair_requests.device_language` · `varchar` · obligatoria
- 🕒 shelf_amount
  > `repair_requests.shelf_amount` · `timestamp` · obligatoria
- 🔗 shelf_id <!-- orbit link: "#shelf_counts", status: "clave foránea" -->
  > `repair_requests.shelf_id` · `int8` · obligatoria
  >
  > Apunta a [`shelf_counts`](#shelf_counts).
- 🔢 fee_ended
  > `repair_requests.fee_ended` · `int4` · opcional
- 🕒 book_code_2
  > `repair_requests.book_code_2` · `timestamp` · opcional
- 🧩 event_updated
  > `repair_requests.event_updated` · `jsonb` · opcional
- 🕒 card_amount
  > `repair_requests.card_amount` · `timestamp` · opcional

### tags
<!-- orbit
id: "tags"
icon: "🧮"
collapsed: true
-->
> `tags` · tabla de Multas y pagos
>
> Una fila por etiqueta, con 7 columnas y unas 114 mil filas.
>
> **Pertenece a** [`hold_links_2`](#hold_links_2)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `tags.id` · `int8` · obligatoria
- ☑️ fee_created
  > `tags.fee_created` · `bool` · obligatoria
- 🕒 loan_archived
  > `tags.loan_archived` · `timestamp` · opcional
- ☑️ review_language
  > `tags.review_language` · `bool` · obligatoria
- 🔢 tag_status
  > `tags.tag_status` · `int4` · opcional
- 🔤 budget_amount
  > `tags.budget_amount` · `varchar` · opcional
- ☑️ copy_total
  > `tags.copy_total` · `bool` · obligatoria

## 📦 Adquisiciones
<!-- orbit
id: "area-acquisitions"
color: "#0EA5E9"
-->
> 9 tablas. Las más grandes: [`member_versions`](#member_versions), [`shelf_counts`](#shelf_counts), [`payment_versions`](#payment_versions).
>
> **Relacionada con** [`donation_requests`](#donation_requests) · [`ebook_versions`](#ebook_versions) · [`copy_history`](#copy_history)

### member_versions
<!-- orbit
id: "member_versions"
icon: "📇"
collapsed: true
-->
> `member_versions` · tabla de Adquisiciones
>
> Una fila por socio, con 9 columnas y unas 355 mil filas.
>
> **Pertenece a** [`fee_requests`](#fee_requests) · [`series_counts`](#series_counts)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `member_versions.id` · `int8` · obligatoria
- 🔢 vendor_name
  > `member_versions.vendor_name` · `int4` · obligatoria
- 🔢 visit_notes
  > `member_versions.visit_notes` · `int4` · obligatoria
- ☑️ event_position
  > `member_versions.event_position` · `bool` · obligatoria
- 🔗 member_id <!-- orbit link: "#member_counts_2", status: "clave foránea" -->
  > `member_versions.member_id` · `int8` · obligatoria
  >
  > Apunta a [`member_counts_2`](#member_counts_2).
- ☑️ audit_active
  > `member_versions.audit_active` · `bool` · opcional
- 🔤 payment_region
  > `member_versions.payment_region` · `varchar` · opcional
- 🕒 branch_created
  > `member_versions.branch_created` · `timestamp` · opcional
- ☑️ shelf_reviewed
  > `member_versions.shelf_reviewed` · `bool` · opcional

### shelf_counts
<!-- orbit
id: "shelf_counts"
icon: "📇"
collapsed: true
-->
> `shelf_counts` · tabla de Adquisiciones
>
> Una fila por estante, con 19 columnas y unas 124 mil filas.
>
> **Pertenece a** [`budget_settings_2`](#budget_settings_2) · [`license_queues_2`](#license_queues_2)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `shelf_counts.id` · `int8` · obligatoria
- 🔤 device_position
  > `shelf_counts.device_position` · `varchar` · opcional
- ☑️ fee_status
  > `shelf_counts.fee_status` · `bool` · opcional
- 🔢 role_label
  > `shelf_counts.role_label` · `int4` · obligatoria
- 🔢 staff_total
  > `shelf_counts.staff_total` · `int4` · opcional
- 🧩 loan_source
  > `shelf_counts.loan_source` · `jsonb` · obligatoria
- 🔢 series_region
  > `shelf_counts.series_region` · `int4` · obligatoria
- 🔢 fee_format
  > `shelf_counts.fee_format` · `int4` · obligatoria
- 🧩 ebook_source
  > `shelf_counts.ebook_source` · `jsonb` · opcional
- 🕒 room_ended
  > `shelf_counts.room_ended` · `timestamp` · opcional
- ☑️ room_reviewed
  > `shelf_counts.room_reviewed` · `bool` · opcional
- ☑️ shelf_position
  > `shelf_counts.shelf_position` · `bool` · obligatoria
- 🔤 event_total
  > `shelf_counts.event_total` · `varchar` · obligatoria
- 🕒 branch_name
  > `shelf_counts.branch_name` · `timestamp` · obligatoria
- 🔗 report_id <!-- orbit link: "#report_links", status: "clave foránea" -->
  > `shelf_counts.report_id` · `int8` · obligatoria
  >
  > Apunta a [`report_links`](#report_links).
- 🕒 notice_started
  > `shelf_counts.notice_started` · `timestamp` · obligatoria
- 🕒 license_amount
  > `shelf_counts.license_amount` · `timestamp` · opcional
- 🔗 license_id <!-- orbit link: "#license_queues_2", status: "clave foránea" -->
  > `shelf_counts.license_id` · `int8` · obligatoria
  >
  > Apunta a [`license_queues_2`](#license_queues_2).
- 🔢 loan_language
  > `shelf_counts.loan_language` · `int4` · obligatoria

### payment_versions
<!-- orbit
id: "payment_versions"
icon: "🧷"
collapsed: true
-->
> `payment_versions` · tabla de Adquisiciones
>
> Una fila por pago, con 5 columnas y unas 504 mil filas.
>
> **Pertenece a** [`hold_logs`](#hold_logs) · [`room_counts`](#room_counts)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `payment_versions.id` · `int8` · obligatoria
- 🔗 copy_id <!-- orbit link: "#copy_history", status: "clave foránea" -->
  > `payment_versions.copy_id` · `int8` · obligatoria
  >
  > Apunta a [`copy_history`](#copy_history).
- 🧩 report_amount
  > `payment_versions.report_amount` · `jsonb` · opcional
- 🧩 card_label
  > `payment_versions.card_label` · `jsonb` · obligatoria
- 🧩 author_started
  > `payment_versions.author_started` · `jsonb` · opcional

### room_counts
<!-- orbit
id: "room_counts"
icon: "🧮"
collapsed: true
-->
> `room_counts` · tabla de Adquisiciones
>
> Una fila por sala, con 18 columnas y unas 653 mil filas.
>
> **Pertenece a** [`audit_links_4`](#audit_links_4) · [`payment_versions`](#payment_versions) · [`license_logs`](#license_logs) · [`room_logs`](#room_logs)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `room_counts.id` · `int8` · obligatoria
- 🔢 license_label
  > `room_counts.license_label` · `int4` · opcional
- 🧩 payment_total
  > `room_counts.payment_total` · `jsonb` · obligatoria
- 🔤 vendor_public
  > `room_counts.vendor_public` · `varchar` · obligatoria
- ☑️ tag_archived
  > `room_counts.tag_archived` · `bool` · opcional
- 🧩 card_source
  > `room_counts.card_source` · `jsonb` · opcional
- 🔢 hold_priority
  > `room_counts.hold_priority` · `int4` · opcional
- ☑️ report_name
  > `room_counts.report_name` · `bool` · opcional
- 🔢 license_notes
  > `room_counts.license_notes` · `int4` · obligatoria
- ☑️ visit_state
  > `room_counts.visit_state` · `bool` · opcional
- 🔗 audit_id <!-- orbit link: "#audit_links_3", status: "clave foránea" -->
  > `room_counts.audit_id` · `int8` · obligatoria
  >
  > Apunta a [`audit_links_3`](#audit_links_3).
- 🔗 events_id <!-- orbit link: "#events", status: "clave foránea" -->
  > `room_counts.events_id` · `int8` · obligatoria
  >
  > Apunta a [`events`](#events).
- 🧩 staff_total
  > `room_counts.staff_total` · `jsonb` · opcional
- ☑️ payment_status
  > `room_counts.payment_status` · `bool` · opcional
- 🧩 author_label
  > `room_counts.author_label` · `jsonb` · opcional
- 🔗 book_id <!-- orbit link: "#book_links", status: "clave foránea" -->
  > `room_counts.book_id` · `int8` · obligatoria
  >
  > Apunta a [`book_links`](#book_links).
- 🔗 repair_id <!-- orbit link: "#repair_requests", status: "clave foránea" -->
  > `room_counts.repair_id` · `int8` · obligatoria
  >
  > Apunta a [`repair_requests`](#repair_requests).
- ☑️ hold_state
  > `room_counts.hold_state` · `bool` · opcional

### order_items
<!-- orbit
id: "order_items"
icon: "🧮"
collapsed: true
-->
> `order_items` · tabla de Adquisiciones
>
> Una fila por pedido, con 14 columnas y unas 879 mil filas.
>
> **Pertenece a** [`license_logs_2`](#license_logs_2) · [`budgets`](#budgets) · [`report_queues`](#report_queues) · [`tag_requests`](#tag_requests)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `order_items.id` · `int8` · obligatoria
- 🔗 rooms_id <!-- orbit link: "#rooms", status: "clave foránea" -->
  > `order_items.rooms_id` · `int8` · obligatoria
  >
  > Apunta a [`rooms`](#rooms).
- 🔢 donation_title
  > `order_items.donation_title` · `int4` · obligatoria
- ☑️ review_language
  > `order_items.review_language` · `bool` · obligatoria
- ☑️ visit_region
  > `order_items.visit_region` · `bool` · opcional
- 🧩 copy_updated
  > `order_items.copy_updated` · `jsonb` · obligatoria
- 🔤 budget_archived
  > `order_items.budget_archived` · `varchar` · opcional
- 🔢 license_code
  > `order_items.license_code` · `int4` · obligatoria
- 🔤 role_title
  > `order_items.role_title` · `varchar` · opcional
- 🕒 order_archived
  > `order_items.order_archived` · `timestamp` · opcional
- 🔤 branch_name
  > `order_items.branch_name` · `varchar` · opcional
- 🧩 license_status
  > `order_items.license_status` · `jsonb` · opcional
- 🧩 payment_total
  > `order_items.payment_total` · `jsonb` · opcional
- 🔢 report_code
  > `order_items.report_code` · `int4` · obligatoria

### report_items
<!-- orbit
id: "report_items"
icon: "🧮"
collapsed: true
-->
> `report_items` · tabla de Adquisiciones
>
> Una fila por informe, con 20 columnas y unas 41 mil filas.
>
> **Pertenece a** [`device_items`](#device_items) · [`fee_requests`](#fee_requests) · [`visit_queues`](#visit_queues)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `report_items.id` · `int8` · obligatoria
- 🧩 report_created
  > `report_items.report_created` · `jsonb` · obligatoria
- 🧩 license_source
  > `report_items.license_source` · `jsonb` · opcional
- 🔢 repair_source
  > `report_items.repair_source` · `int4` · opcional
- 🧩 event_ended
  > `report_items.event_ended` · `jsonb` · obligatoria
- 🔢 loan_title
  > `report_items.loan_title` · `int4` · opcional
- 🔗 donation_id <!-- orbit link: "#donation_requests", status: "clave foránea" -->
  > `report_items.donation_id` · `int8` · obligatoria
  >
  > Apunta a [`donation_requests`](#donation_requests).
- 🔤 ebook_amount
  > `report_items.ebook_amount` · `varchar` · opcional
- 🔢 shelf_archived
  > `report_items.shelf_archived` · `int4` · opcional
- 🧩 order_created
  > `report_items.order_created` · `jsonb` · obligatoria
- 🧩 device_code
  > `report_items.device_code` · `jsonb` · opcional
- 🧩 visit_state
  > `report_items.visit_state` · `jsonb` · obligatoria
- 🕒 branch_code
  > `report_items.branch_code` · `timestamp` · opcional
- 🧩 copy_public
  > `report_items.copy_public` · `jsonb` · opcional
- 🔢 shelf_public
  > `report_items.shelf_public` · `int4` · opcional
- 🧩 ebook_label
  > `report_items.ebook_label` · `jsonb` · opcional
- 🧩 author_ended
  > `report_items.author_ended` · `jsonb` · obligatoria
- 🧩 ebook_name
  > `report_items.ebook_name` · `jsonb` · obligatoria
- 🔤 budget_public
  > `report_items.budget_public` · `varchar` · obligatoria
- 🧩 loan_kind
  > `report_items.loan_kind` · `jsonb` · obligatoria

### ebook_queues
<!-- orbit
id: "ebook_queues"
icon: "📇"
collapsed: true
-->
> `ebook_queues` · tabla de Adquisiciones
>
> Una fila por libro electrónico, con 4 columnas y unas 346 mil filas.
>
> **Pertenece a** [`book_links`](#book_links) · [`license_logs`](#license_logs) · [`card_links_2`](#card_links_2) · [`donation_links`](#donation_links)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `ebook_queues.id` · `int8` · obligatoria
- 🕒 license_position
  > `ebook_queues.license_position` · `timestamp` · obligatoria
- 🕒 event_count
  > `ebook_queues.event_count` · `timestamp` · obligatoria
- 🕒 fee_format
  > `ebook_queues.fee_format` · `timestamp` · obligatoria

### license_queues
<!-- orbit
id: "license_queues"
icon: "🧷"
collapsed: true
-->
> `license_queues` · tabla de Adquisiciones
>
> Una fila por licencia, con 9 columnas y unas 480 mil filas.
>
> **Pertenece a** [`review_queues`](#review_queues) · [`ebook_versions`](#ebook_versions) · [`fee_requests`](#fee_requests) · [`series_requests`](#series_requests)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `license_queues.id` · `int8` · obligatoria
- 🕒 payment_priority
  > `license_queues.payment_priority` · `timestamp` · obligatoria
- ☑️ donation_reviewed
  > `license_queues.donation_reviewed` · `bool` · obligatoria
- ☑️ fee_total
  > `license_queues.fee_total` · `bool` · obligatoria
- 🧩 book_amount
  > `license_queues.book_amount` · `jsonb` · obligatoria
- 🔗 tag_id <!-- orbit link: "#tag_requests", status: "clave foránea" -->
  > `license_queues.tag_id` · `int8` · obligatoria
  >
  > Apunta a [`tag_requests`](#tag_requests).
- 🔢 series_notes
  > `license_queues.series_notes` · `int4` · obligatoria
- ☑️ budget_code
  > `license_queues.budget_code` · `bool` · obligatoria
- 🔤 ebook_started
  > `license_queues.ebook_started` · `varchar` · obligatoria

### notice_requests
<!-- orbit
id: "notice_requests"
icon: "🧮"
collapsed: true
-->
> `notice_requests` · tabla de Adquisiciones
>
> Una fila por aviso, con 5 columnas y unas 269 mil filas.
>
> **Pertenece a** [`report_logs`](#report_logs)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `notice_requests.id` · `int8` · obligatoria
- 🧩 series_created
  > `notice_requests.series_created` · `jsonb` · opcional
- 🕒 vendor_external
  > `notice_requests.vendor_external` · `timestamp` · obligatoria
- 🔗 role_id <!-- orbit link: "#role_counts", status: "clave foránea" -->
  > `notice_requests.role_id` · `int8` · obligatoria
  >
  > Apunta a [`role_counts`](#role_counts).
- 🔢 budget_state
  > `notice_requests.budget_state` · `int4` · opcional

## 🧾 Informes
<!-- orbit
id: "area-reports"
color: "#64748B"
-->
> 12 tablas. Las más grandes: [`ebook_logs`](#ebook_logs), [`vendor_history`](#vendor_history), [`device_items`](#device_items).
>
> **Relacionada con** [`order_logs`](#order_logs) · [`report_logs_2`](#report_logs_2) · [`audit_settings`](#audit_settings)

### ebook_logs
<!-- orbit
id: "ebook_logs"
icon: "🧮"
collapsed: true
-->
> `ebook_logs` · tabla de Informes
>
> Una fila por libro electrónico, con 70 columnas y unas 735 mil filas.
>
> **Pertenece a** [`audit_links_3`](#audit_links_3) · [`member_counts`](#member_counts) · [`audit_settings`](#audit_settings)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `ebook_logs.id` · `int8` · obligatoria
- 🧩 donation_updated
  > `ebook_logs.donation_updated` · `jsonb` · obligatoria
- 🔢 audit_format
  > `ebook_logs.audit_format` · `int4` · obligatoria
- 🔤 review_kind
  > `ebook_logs.review_kind` · `varchar` · obligatoria
- 🧩 donation_count
  > `ebook_logs.donation_count` · `jsonb` · obligatoria
- 🔢 shelf_total
  > `ebook_logs.shelf_total` · `int4` · opcional
- 🔗 visit_id <!-- orbit link: "#visit_items", status: "clave foránea" -->
  > `ebook_logs.visit_id` · `int8` · obligatoria
  >
  > Apunta a [`visit_items`](#visit_items).
- ☑️ role_ended
  > `ebook_logs.role_ended` · `bool` · opcional
- 🔤 audit_archived
  > `ebook_logs.audit_archived` · `varchar` · opcional
- 🔢 order_format
  > `ebook_logs.order_format` · `int4` · obligatoria
- 🧩 series_kind
  > `ebook_logs.series_kind` · `jsonb` · opcional
- 🔤 copy_position
  > `ebook_logs.copy_position` · `varchar` · opcional
- 🧩 author_code
  > `ebook_logs.author_code` · `jsonb` · obligatoria
- 🔗 budget_id <!-- orbit link: "#budget_settings", status: "clave foránea" -->
  > `ebook_logs.budget_id` · `int8` · obligatoria
  >
  > Apunta a [`budget_settings`](#budget_settings).
- 🧩 series_name
  > `ebook_logs.series_name` · `jsonb` · opcional
- ☑️ author_active
  > `ebook_logs.author_active` · `bool` · opcional
- ☑️ hold_title
  > `ebook_logs.hold_title` · `bool` · obligatoria
- ☑️ notice_notes
  > `ebook_logs.notice_notes` · `bool` · opcional
- 🔢 device_updated
  > `ebook_logs.device_updated` · `int4` · opcional
- 🔢 report_kind
  > `ebook_logs.report_kind` · `int4` · obligatoria
- ☑️ hold_external
  > `ebook_logs.hold_external` · `bool` · opcional
- 🕒 report_notes
  > `ebook_logs.report_notes` · `timestamp` · opcional
- 🕒 room_reviewed
  > `ebook_logs.room_reviewed` · `timestamp` · obligatoria
- ☑️ report_updated
  > `ebook_logs.report_updated` · `bool` · opcional
- 🔗 review_id <!-- orbit link: "#review_links", status: "clave foránea" -->
  > `ebook_logs.review_id` · `int8` · obligatoria
  >
  > Apunta a [`review_links`](#review_links).
- 🔗 ebook_id <!-- orbit link: "#ebook_requests", status: "clave foránea" -->
  > `ebook_logs.ebook_id` · `int8` · obligatoria
  >
  > Apunta a [`ebook_requests`](#ebook_requests).
- ☑️ author_external
  > `ebook_logs.author_external` · `bool` · obligatoria
- 🔤 book_label
  > `ebook_logs.book_label` · `varchar` · opcional
- 🔗 member_id <!-- orbit link: "#member_versions", status: "clave foránea" -->
  > `ebook_logs.member_id` · `int8` · obligatoria
  >
  > Apunta a [`member_versions`](#member_versions).
- 🔤 copy_status
  > `ebook_logs.copy_status` · `varchar` · opcional
- ☑️ copy_amount
  > `ebook_logs.copy_amount` · `bool` · obligatoria
- ☑️ book_reviewed
  > `ebook_logs.book_reviewed` · `bool` · obligatoria
- 🕒 license_priority
  > `ebook_logs.license_priority` · `timestamp` · obligatoria
- ☑️ card_count
  > `ebook_logs.card_count` · `bool` · obligatoria
- 🧩 report_notes_2
  > `ebook_logs.report_notes_2` · `jsonb` · obligatoria
- 🔢 notice_language
  > `ebook_logs.notice_language` · `int4` · opcional
- 🕒 branch_position
  > `ebook_logs.branch_position` · `timestamp` · opcional
- 🔗 role_id <!-- orbit link: "#role_counts", status: "clave foránea" -->
  > `ebook_logs.role_id` · `int8` · obligatoria
  >
  > Apunta a [`role_counts`](#role_counts).
- 🔤 license_language
  > `ebook_logs.license_language` · `varchar` · opcional
- 🧩 review_ended
  > `ebook_logs.review_ended` · `jsonb` · obligatoria
- 🔗 device_id <!-- orbit link: "#device_items", status: "clave foránea" -->
  > `ebook_logs.device_id` · `int8` · obligatoria
  >
  > Apunta a [`device_items`](#device_items).
- 🔗 card_id <!-- orbit link: "#card_queues", status: "clave foránea" -->
  > `ebook_logs.card_id` · `int8` · obligatoria
  >
  > Apunta a [`card_queues`](#card_queues).
- 🔤 payment_notes
  > `ebook_logs.payment_notes` · `varchar` · opcional
- 🔗 author_id <!-- orbit link: "#author_links_2", status: "clave foránea" -->
  > `ebook_logs.author_id` · `int8` · obligatoria
  >
  > Apunta a [`author_links_2`](#author_links_2).
- 🕒 staff_notes
  > `ebook_logs.staff_notes` · `timestamp` · obligatoria
- 🔗 repair_id <!-- orbit link: "#repair_requests", status: "clave foránea" -->
  > `ebook_logs.repair_id` · `int8` · obligatoria
  >
  > Apunta a [`repair_requests`](#repair_requests).
- 🔤 member_reviewed
  > `ebook_logs.member_reviewed` · `varchar` · obligatoria
- 🔗 book_id <!-- orbit link: "#book_settings", status: "clave foránea" -->
  > `ebook_logs.book_id` · `int8` · obligatoria
  >
  > Apunta a [`book_settings`](#book_settings).
- 🔢 audit_count
  > `ebook_logs.audit_count` · `int4` · opcional
- ☑️ card_active
  > `ebook_logs.card_active` · `bool` · obligatoria
- 🔗 budgets_id <!-- orbit link: "#budgets", status: "clave foránea" -->
  > `ebook_logs.budgets_id` · `int8` · obligatoria
  >
  > Apunta a [`budgets`](#budgets).
- 🔤 role_kind
  > `ebook_logs.role_kind` · `varchar` · opcional
- 🔤 report_name
  > `ebook_logs.report_name` · `varchar` · opcional
- ☑️ fee_label
  > `ebook_logs.fee_label` · `bool` · obligatoria
- 🕒 role_position
  > `ebook_logs.role_position` · `timestamp` · obligatoria
- 🔗 author_id_2 <!-- orbit link: "#author_history", status: "clave foránea" -->
  > `ebook_logs.author_id_2` · `int8` · obligatoria
  >
  > Apunta a [`author_history`](#author_history).
- 🔗 audit_id <!-- orbit link: "#audit_settings", status: "clave foránea" -->
  > `ebook_logs.audit_id` · `int8` · obligatoria
  >
  > Apunta a [`audit_settings`](#audit_settings).
- 🔢 event_state
  > `ebook_logs.event_state` · `int4` · obligatoria
- 🕒 vendor_total
  > `ebook_logs.vendor_total` · `timestamp` · opcional
- 🕒 vendor_format
  > `ebook_logs.vendor_format` · `timestamp` · obligatoria
- 🧩 hold_active
  > `ebook_logs.hold_active` · `jsonb` · obligatoria
- ☑️ budget_name
  > `ebook_logs.budget_name` · `bool` · opcional
- 🔢 device_format
  > `ebook_logs.device_format` · `int4` · obligatoria
- 🧩 vendor_region
  > `ebook_logs.vendor_region` · `jsonb` · obligatoria
- 🔤 device_external
  > `ebook_logs.device_external` · `varchar` · obligatoria
- 🔗 card_id_2 <!-- orbit link: "#card_queues", status: "clave foránea" -->
  > `ebook_logs.card_id_2` · `int8` · obligatoria
  >
  > Apunta a [`card_queues`](#card_queues).
- 🕒 visit_format
  > `ebook_logs.visit_format` · `timestamp` · obligatoria
- ☑️ room_updated
  > `ebook_logs.room_updated` · `bool` · obligatoria
- 🧩 room_title
  > `ebook_logs.room_title` · `jsonb` · obligatoria
- 🔗 order_id <!-- orbit link: "#order_links", status: "clave foránea" -->
  > `ebook_logs.order_id` · `int8` · obligatoria
  >
  > Apunta a [`order_links`](#order_links).

### vendor_history
<!-- orbit
id: "vendor_history"
icon: "🧷"
collapsed: true
-->
> `vendor_history` · tabla de Informes
>
> Una fila por proveedor, con 20 columnas y unas 744 mil filas.
>
> **Pertenece a** [`series_links`](#series_links) · [`card_queues`](#card_queues) · [`room_counts`](#room_counts)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `vendor_history.id` · `int8` · obligatoria
- 🔢 budget_code
  > `vendor_history.budget_code` · `int4` · obligatoria
- 🔤 license_title
  > `vendor_history.license_title` · `varchar` · opcional
- 🕒 role_public
  > `vendor_history.role_public` · `timestamp` · obligatoria
- ☑️ hold_created
  > `vendor_history.hold_created` · `bool` · obligatoria
- 🔢 loan_count
  > `vendor_history.loan_count` · `int4` · obligatoria
- 🔗 events_id <!-- orbit link: "#events", status: "clave foránea" -->
  > `vendor_history.events_id` · `int8` · obligatoria
  >
  > Apunta a [`events`](#events).
- 🔤 member_priority
  > `vendor_history.member_priority` · `varchar` · obligatoria
- ☑️ member_ended
  > `vendor_history.member_ended` · `bool` · obligatoria
- ☑️ vendor_total
  > `vendor_history.vendor_total` · `bool` · obligatoria
- ☑️ book_started
  > `vendor_history.book_started` · `bool` · obligatoria
- 🕒 book_title
  > `vendor_history.book_title` · `timestamp` · opcional
- 🔤 visit_format
  > `vendor_history.visit_format` · `varchar` · obligatoria
- 🔢 budget_started
  > `vendor_history.budget_started` · `int4` · obligatoria
- 🔢 order_updated
  > `vendor_history.order_updated` · `int4` · obligatoria
- 🔤 report_started
  > `vendor_history.report_started` · `varchar` · obligatoria
- 🔤 report_region
  > `vendor_history.report_region` · `varchar` · opcional
- 🔤 budget_total
  > `vendor_history.budget_total` · `varchar` · opcional
- ☑️ notice_code
  > `vendor_history.notice_code` · `bool` · opcional
- 🕒 license_position
  > `vendor_history.license_position` · `timestamp` · opcional

### device_items
<!-- orbit
id: "device_items"
icon: "🗃️"
collapsed: true
-->
> `device_items` · tabla de Informes
>
> Una fila por dispositivo, con 9 columnas y unas 649 mil filas.
>
> **Pertenece a** [`vendor_items`](#vendor_items) · [`notice_requests`](#notice_requests) · [`vendor_history`](#vendor_history) · [`order_logs`](#order_logs)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `device_items.id` · `int8` · obligatoria
- 🧩 room_active
  > `device_items.room_active` · `jsonb` · opcional
- 🧩 role_created
  > `device_items.role_created` · `jsonb` · obligatoria
- 🕒 report_ended
  > `device_items.report_ended` · `timestamp` · opcional
- 🔗 vendor_id <!-- orbit link: "#vendor_counts", status: "clave foránea" -->
  > `device_items.vendor_id` · `int8` · obligatoria
  >
  > Apunta a [`vendor_counts`](#vendor_counts).
- ☑️ report_language
  > `device_items.report_language` · `bool` · opcional
- 🔢 book_code
  > `device_items.book_code` · `int4` · obligatoria
- 🔤 role_state
  > `device_items.role_state` · `varchar` · opcional
- 🧩 loan_title
  > `device_items.loan_title` · `jsonb` · opcional

### visits
<!-- orbit
id: "visits"
icon: "📗"
collapsed: true
-->
> `visits` · tabla de Informes
>
> Una fila por visita, con 4 columnas y unas 220 mil filas.
>
> **Pertenece a** [`author_links_3`](#author_links_3) · [`room_counts`](#room_counts) · [`audit_links`](#audit_links) · [`series_history`](#series_history)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `visits.id` · `int8` · obligatoria
- 🔢 donation_kind
  > `visits.donation_kind` · `int4` · obligatoria
- ☑️ loan_reviewed
  > `visits.loan_reviewed` · `bool` · opcional
- 🔢 repair_active
  > `visits.repair_active` · `int4` · opcional

### donation_history
<!-- orbit
id: "donation_history"
icon: "📇"
collapsed: true
-->
> `donation_history` · tabla de Informes
>
> Una fila por donación, con 16 columnas y unas 652 mil filas.
>
> **Pertenece a** [`report_links`](#report_links)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `donation_history.id` · `int8` · obligatoria
- 🔗 hold_id <!-- orbit link: "#hold_logs", status: "clave foránea" -->
  > `donation_history.hold_id` · `int8` · obligatoria
  >
  > Apunta a [`hold_logs`](#hold_logs).
- 🔢 repair_state
  > `donation_history.repair_state` · `int4` · opcional
- 🔤 audit_notes
  > `donation_history.audit_notes` · `varchar` · opcional
- ☑️ tag_name
  > `donation_history.tag_name` · `bool` · obligatoria
- 🔤 copy_title
  > `donation_history.copy_title` · `varchar` · opcional
- 🕒 book_code
  > `donation_history.book_code` · `timestamp` · obligatoria
- 🔤 audit_notes_2
  > `donation_history.audit_notes_2` · `varchar` · opcional
- 🕒 hold_started
  > `donation_history.hold_started` · `timestamp` · obligatoria
- 🧩 tag_status
  > `donation_history.tag_status` · `jsonb` · obligatoria
- 🔤 author_format
  > `donation_history.author_format` · `varchar` · obligatoria
- 🔤 review_state
  > `donation_history.review_state` · `varchar` · obligatoria
- 🔢 visit_kind
  > `donation_history.visit_kind` · `int4` · obligatoria
- 🕒 report_total
  > `donation_history.report_total` · `timestamp` · opcional
- 🔗 room_id <!-- orbit link: "#room_logs", status: "clave foránea" -->
  > `donation_history.room_id` · `int8` · obligatoria
  >
  > Apunta a [`room_logs`](#room_logs).
- 🧩 visit_count
  > `donation_history.visit_count` · `jsonb` · opcional

### hold_links_2
<!-- orbit
id: "hold_links_2"
icon: "🗃️"
collapsed: true
-->
> `hold_links_2` · tabla de Informes
>
> Una fila por reserva, con 5 columnas y unas 648 mil filas.
>
> **Pertenece a** [`hold_links`](#hold_links)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `hold_links_2.id` · `int8` · obligatoria
- 🔤 audit_state
  > `hold_links_2.audit_state` · `varchar` · opcional
- 🔤 loan_source
  > `hold_links_2.loan_source` · `varchar` · obligatoria
- ☑️ fee_ended
  > `hold_links_2.fee_ended` · `bool` · obligatoria
- 🕒 book_ended
  > `hold_links_2.book_ended` · `timestamp` · obligatoria

### license_queues_2
<!-- orbit
id: "license_queues_2"
icon: "🧮"
collapsed: true
-->
> `license_queues_2` · tabla de Informes
>
> Una fila por licencia, con 5 columnas y unas 292 mil filas.
>
> **Pertenece a** [`member_history`](#member_history) · [`donation_history`](#donation_history)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `license_queues_2.id` · `int8` · obligatoria
- 🔢 audit_state
  > `license_queues_2.audit_state` · `int4` · obligatoria
- 🔢 device_active
  > `license_queues_2.device_active` · `int4` · obligatoria
- ☑️ copy_format
  > `license_queues_2.copy_format` · `bool` · obligatoria
- 🧩 series_public
  > `license_queues_2.series_public` · `jsonb` · obligatoria

### rooms
<!-- orbit
id: "rooms"
icon: "📇"
collapsed: true
-->
> `rooms` · tabla de Informes
>
> Una fila por sala, con 23 columnas y unas 49 mil filas.
>
> **Pertenece a** [`visit_queues`](#visit_queues)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `rooms.id` · `int8` · obligatoria
- 🔤 donation_external
  > `rooms.donation_external` · `varchar` · opcional
- 🔤 role_started
  > `rooms.role_started` · `varchar` · obligatoria
- 🔤 visit_priority
  > `rooms.visit_priority` · `varchar` · obligatoria
- 🧩 report_format
  > `rooms.report_format` · `jsonb` · opcional
- 🔗 event_id <!-- orbit link: "#event_history", status: "clave foránea" -->
  > `rooms.event_id` · `int8` · obligatoria
  >
  > Apunta a [`event_history`](#event_history).
- 🔢 book_amount
  > `rooms.book_amount` · `int4` · obligatoria
- 🧩 role_label
  > `rooms.role_label` · `jsonb` · obligatoria
- 🧩 series_kind
  > `rooms.series_kind` · `jsonb` · opcional
- 🕒 ebook_reviewed
  > `rooms.ebook_reviewed` · `timestamp` · opcional
- 🕒 audit_public
  > `rooms.audit_public` · `timestamp` · opcional
- 🔤 copy_updated
  > `rooms.copy_updated` · `varchar` · opcional
- 🔢 device_state
  > `rooms.device_state` · `int4` · opcional
- 🧩 card_total
  > `rooms.card_total` · `jsonb` · opcional
- 🕒 hold_kind
  > `rooms.hold_kind` · `timestamp` · opcional
- ☑️ loan_amount
  > `rooms.loan_amount` · `bool` · obligatoria
- 🧩 book_position
  > `rooms.book_position` · `jsonb` · opcional
- 🔢 device_format
  > `rooms.device_format` · `int4` · opcional
- 🔢 author_public
  > `rooms.author_public` · `int4` · opcional
- 🔢 role_public
  > `rooms.role_public` · `int4` · obligatoria
- 🔢 role_position
  > `rooms.role_position` · `int4` · obligatoria
- 🕒 shelf_label
  > `rooms.shelf_label` · `timestamp` · obligatoria
- ☑️ budget_code
  > `rooms.budget_code` · `bool` · opcional

### loan_logs
<!-- orbit
id: "loan_logs"
icon: "📇"
collapsed: true
-->
> `loan_logs` · tabla de Informes
>
> Una fila por préstamo, con 11 columnas y unas 524 mil filas.
>
> **Pertenece a** [`order_logs`](#order_logs) · [`audit_links_4`](#audit_links_4) · [`notice_counts`](#notice_counts)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `loan_logs.id` · `int8` · obligatoria
- ☑️ card_language
  > `loan_logs.card_language` · `bool` · obligatoria
- 🕒 book_count
  > `loan_logs.book_count` · `timestamp` · opcional
- ☑️ vendor_count
  > `loan_logs.vendor_count` · `bool` · obligatoria
- 🕒 author_external
  > `loan_logs.author_external` · `timestamp` · obligatoria
- 🔗 book_id <!-- orbit link: "#book_items", status: "clave foránea" -->
  > `loan_logs.book_id` · `int8` · obligatoria
  >
  > Apunta a [`book_items`](#book_items).
- 🔢 device_label
  > `loan_logs.device_label` · `int4` · opcional
- 🧩 audit_created
  > `loan_logs.audit_created` · `jsonb` · obligatoria
- 🧩 vendor_title
  > `loan_logs.vendor_title` · `jsonb` · opcional
- ☑️ ebook_code
  > `loan_logs.ebook_code` · `bool` · obligatoria
- 🔗 event_id <!-- orbit link: "#event_history", status: "clave foránea" -->
  > `loan_logs.event_id` · `int8` · obligatoria
  >
  > Apunta a [`event_history`](#event_history).

### review_queues
<!-- orbit
id: "review_queues"
icon: "📇"
collapsed: true
-->
> `review_queues` · tabla de Informes
>
> Una fila por reseña, con 20 columnas y unas 814 mil filas.
>
> **Pertenece a** [`member_counts_2`](#member_counts_2)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `review_queues.id` · `int8` · obligatoria
- 🧩 vendor_label
  > `review_queues.vendor_label` · `jsonb` · obligatoria
- 🔢 donation_archived
  > `review_queues.donation_archived` · `int4` · obligatoria
- 🔗 donation_id <!-- orbit link: "#donation_requests", status: "clave foránea" -->
  > `review_queues.donation_id` · `int8` · obligatoria
  >
  > Apunta a [`donation_requests`](#donation_requests).
- 🔢 event_language
  > `review_queues.event_language` · `int4` · opcional
- 🔤 hold_region
  > `review_queues.hold_region` · `varchar` · obligatoria
- 🔢 staff_active
  > `review_queues.staff_active` · `int4` · opcional
- 🔤 event_name
  > `review_queues.event_name` · `varchar` · obligatoria
- ☑️ staff_name
  > `review_queues.staff_name` · `bool` · opcional
- 🧩 author_kind
  > `review_queues.author_kind` · `jsonb` · opcional
- 🔢 staff_name_2
  > `review_queues.staff_name_2` · `int4` · opcional
- 🧩 series_name
  > `review_queues.series_name` · `jsonb` · obligatoria
- 🔗 report_id <!-- orbit link: "#report_queues_2", status: "clave foránea" -->
  > `review_queues.report_id` · `int8` · obligatoria
  >
  > Apunta a [`report_queues_2`](#report_queues_2).
- 🕒 review_region
  > `review_queues.review_region` · `timestamp` · obligatoria
- 🔗 staff_id <!-- orbit link: "#staff_items", status: "clave foránea" -->
  > `review_queues.staff_id` · `int8` · obligatoria
  >
  > Apunta a [`staff_items`](#staff_items).
- 🔤 vendor_total
  > `review_queues.vendor_total` · `varchar` · opcional
- 🔤 tag_kind
  > `review_queues.tag_kind` · `varchar` · opcional
- 🔗 author_id <!-- orbit link: "#author_links_3", status: "clave foránea" -->
  > `review_queues.author_id` · `int8` · obligatoria
  >
  > Apunta a [`author_links_3`](#author_links_3).
- 🧩 budget_count
  > `review_queues.budget_count` · `jsonb` · opcional
- 🔗 staff_id_2 <!-- orbit link: "#staff_counts", status: "clave foránea" -->
  > `review_queues.staff_id_2` · `int8` · obligatoria
  >
  > Apunta a [`staff_counts`](#staff_counts).

### tag_settings_2
<!-- orbit
id: "tag_settings_2"
icon: "🧷"
collapsed: true
-->
> `tag_settings_2` · tabla de Informes
>
> Una fila por etiqueta, con 18 columnas y unas 409 mil filas.
>
> **Pertenece a** [`visit_queues`](#visit_queues)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `tag_settings_2.id` · `int8` · obligatoria
- 🔗 book_id <!-- orbit link: "#book_items", status: "clave foránea" -->
  > `tag_settings_2.book_id` · `int8` · obligatoria
  >
  > Apunta a [`book_items`](#book_items).
- 🧩 card_region
  > `tag_settings_2.card_region` · `jsonb` · obligatoria
- 🔗 card_id <!-- orbit link: "#card_queues", status: "clave foránea" -->
  > `tag_settings_2.card_id` · `int8` · obligatoria
  >
  > Apunta a [`card_queues`](#card_queues).
- 🔢 event_updated
  > `tag_settings_2.event_updated` · `int4` · opcional
- ☑️ loan_external
  > `tag_settings_2.loan_external` · `bool` · opcional
- 🕒 hold_kind
  > `tag_settings_2.hold_kind` · `timestamp` · obligatoria
- 🔤 device_notes
  > `tag_settings_2.device_notes` · `varchar` · opcional
- ☑️ branch_active
  > `tag_settings_2.branch_active` · `bool` · obligatoria
- ☑️ member_public
  > `tag_settings_2.member_public` · `bool` · obligatoria
- 🕒 hold_ended
  > `tag_settings_2.hold_ended` · `timestamp` · obligatoria
- 🔢 payment_title
  > `tag_settings_2.payment_title` · `int4` · obligatoria
- 🧩 fee_updated
  > `tag_settings_2.fee_updated` · `jsonb` · obligatoria
- ☑️ budget_public
  > `tag_settings_2.budget_public` · `bool` · opcional
- 🕒 budget_position
  > `tag_settings_2.budget_position` · `timestamp` · obligatoria
- 🔢 review_active
  > `tag_settings_2.review_active` · `int4` · opcional
- ☑️ visit_active
  > `tag_settings_2.visit_active` · `bool` · obligatoria
- 🔗 shelf_id <!-- orbit link: "#shelf_counts", status: "clave foránea" -->
  > `tag_settings_2.shelf_id` · `int8` · obligatoria
  >
  > Apunta a [`shelf_counts`](#shelf_counts).

### series_history
<!-- orbit
id: "series_history"
icon: "🧷"
collapsed: true
-->
> `series_history` · tabla de Informes
>
> Una fila por serie, con 17 columnas y unas 267 mil filas.
>
> **Pertenece a** [`review_items`](#review_items) · [`author_history`](#author_history) · [`tag_settings_2`](#tag_settings_2) · [`member_history`](#member_history)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `series_history.id` · `int8` · obligatoria
- 🧩 license_kind
  > `series_history.license_kind` · `jsonb` · opcional
- 🔤 book_kind
  > `series_history.book_kind` · `varchar` · obligatoria
- 🧩 repair_region
  > `series_history.repair_region` · `jsonb` · obligatoria
- 🕒 author_ended
  > `series_history.author_ended` · `timestamp` · obligatoria
- ☑️ copy_region
  > `series_history.copy_region` · `bool` · obligatoria
- 🔗 loan_id <!-- orbit link: "#loan_history", status: "clave foránea" -->
  > `series_history.loan_id` · `int8` · obligatoria
  >
  > Apunta a [`loan_history`](#loan_history).
- 🔤 order_notes
  > `series_history.order_notes` · `varchar` · obligatoria
- ☑️ room_priority
  > `series_history.room_priority` · `bool` · obligatoria
- 🔗 event_id <!-- orbit link: "#event_history", status: "clave foránea" -->
  > `series_history.event_id` · `int8` · obligatoria
  >
  > Apunta a [`event_history`](#event_history).
- 🧩 staff_started
  > `series_history.staff_started` · `jsonb` · obligatoria
- 🔗 ebook_id <!-- orbit link: "#ebook_queues", status: "clave foránea" -->
  > `series_history.ebook_id` · `int8` · obligatoria
  >
  > Apunta a [`ebook_queues`](#ebook_queues).
- 🕒 ebook_started
  > `series_history.ebook_started` · `timestamp` · obligatoria
- 🔢 vendor_region
  > `series_history.vendor_region` · `int4` · opcional
- ☑️ tag_code
  > `series_history.tag_code` · `bool` · opcional
- 🔗 copy_id <!-- orbit link: "#copy_versions", status: "clave foránea" -->
  > `series_history.copy_id` · `int8` · obligatoria
  >
  > Apunta a [`copy_versions`](#copy_versions).
- 🧩 audit_archived
  > `series_history.audit_archived` · `jsonb` · obligatoria

## 🔐 Personal y accesos
<!-- orbit
id: "area-staff-access"
color: "#9333EA"
-->
> 12 tablas. Las más grandes: [`order_links`](#order_links), [`license_logs_2`](#license_logs_2), [`payment_requests`](#payment_requests).
>
> **Relacionada con** [`donations`](#donations) · [`review_logs`](#review_logs) · [`budget_queues`](#budget_queues)

### order_links
<!-- orbit
id: "order_links"
icon: "📇"
collapsed: true
-->
> `order_links` · tabla de Personal y accesos
>
> Una fila por pedido, con 18 columnas y unas 360 mil filas.
>
> **Pertenece a** [`loan_settings`](#loan_settings) · [`review_logs`](#review_logs) · [`staff_counts`](#staff_counts)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `order_links.id` · `int8` · obligatoria
- 🔢 device_external
  > `order_links.device_external` · `int4` · opcional
- 🧩 report_notes
  > `order_links.report_notes` · `jsonb` · opcional
- 🧩 repair_code
  > `order_links.repair_code` · `jsonb` · obligatoria
- 🔗 fee_id <!-- orbit link: "#fee_requests", status: "clave foránea" -->
  > `order_links.fee_id` · `int8` · obligatoria
  >
  > Apunta a [`fee_requests`](#fee_requests).
- ☑️ notice_language
  > `order_links.notice_language` · `bool` · opcional
- 🔤 branch_public
  > `order_links.branch_public` · `varchar` · obligatoria
- 🕒 fee_source
  > `order_links.fee_source` · `timestamp` · obligatoria
- 🔢 card_archived
  > `order_links.card_archived` · `int4` · opcional
- 🧩 card_external
  > `order_links.card_external` · `jsonb` · opcional
- 🕒 room_active
  > `order_links.room_active` · `timestamp` · opcional
- 🕒 member_total
  > `order_links.member_total` · `timestamp` · obligatoria
- 🔤 budget_title
  > `order_links.budget_title` · `varchar` · opcional
- 🧩 room_count
  > `order_links.room_count` · `jsonb` · opcional
- 🧩 room_started
  > `order_links.room_started` · `jsonb` · obligatoria
- ☑️ ebook_label
  > `order_links.ebook_label` · `bool` · opcional
- ☑️ audit_archived
  > `order_links.audit_archived` · `bool` · opcional
- 🔗 order_id <!-- orbit link: "#order_versions", status: "clave foránea" -->
  > `order_links.order_id` · `int8` · obligatoria
  >
  > Apunta a [`order_versions`](#order_versions).

### license_logs_2
<!-- orbit
id: "license_logs_2"
icon: "📗"
collapsed: true
-->
> `license_logs_2` · tabla de Personal y accesos
>
> Una fila por licencia, con 19 columnas y unas 515 mil filas.
>
> **Pertenece a** [`author_links_3`](#author_links_3) · [`order_items`](#order_items) · [`series_requests`](#series_requests) · [`room_counts_2`](#room_counts_2)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `license_logs_2.id` · `int8` · obligatoria
- ☑️ member_language
  > `license_logs_2.member_language` · `bool` · opcional
- 🔗 card_id <!-- orbit link: "#card_links_2", status: "clave foránea" -->
  > `license_logs_2.card_id` · `int8` · obligatoria
  >
  > Apunta a [`card_links_2`](#card_links_2).
- 🔢 member_reviewed
  > `license_logs_2.member_reviewed` · `int4` · opcional
- 🧩 book_ended
  > `license_logs_2.book_ended` · `jsonb` · opcional
- 🔗 report_id <!-- orbit link: "#report_queues", status: "clave foránea" -->
  > `license_logs_2.report_id` · `int8` · obligatoria
  >
  > Apunta a [`report_queues`](#report_queues).
- 🕒 order_notes
  > `license_logs_2.order_notes` · `timestamp` · obligatoria
- 🔗 visit_id <!-- orbit link: "#visit_links", status: "clave foránea" -->
  > `license_logs_2.visit_id` · `int8` · obligatoria
  >
  > Apunta a [`visit_links`](#visit_links).
- 🔤 budget_total
  > `license_logs_2.budget_total` · `varchar` · obligatoria
- 🧩 tag_position
  > `license_logs_2.tag_position` · `jsonb` · opcional
- 🔢 tag_public
  > `license_logs_2.tag_public` · `int4` · obligatoria
- ☑️ audit_reviewed
  > `license_logs_2.audit_reviewed` · `bool` · obligatoria
- ☑️ card_code
  > `license_logs_2.card_code` · `bool` · opcional
- 🕒 card_label
  > `license_logs_2.card_label` · `timestamp` · opcional
- ☑️ book_state
  > `license_logs_2.book_state` · `bool` · obligatoria
- 🔢 loan_kind
  > `license_logs_2.loan_kind` · `int4` · opcional
- ☑️ order_count
  > `license_logs_2.order_count` · `bool` · opcional
- 🧩 event_state
  > `license_logs_2.event_state` · `jsonb` · opcional
- 🔤 fee_format
  > `license_logs_2.fee_format` · `varchar` · obligatoria

### payment_requests
<!-- orbit
id: "payment_requests"
icon: "🗃️"
collapsed: true
-->
> `payment_requests` · tabla de Personal y accesos
>
> Una fila por pago, con 21 columnas y unas 372 mil filas.
>
> **Pertenece a** [`card_logs`](#card_logs) · [`license_versions`](#license_versions) · [`vendor_counts`](#vendor_counts) · [`member_counts`](#member_counts)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `payment_requests.id` · `int8` · obligatoria
- ☑️ review_position
  > `payment_requests.review_position` · `bool` · obligatoria
- ☑️ donation_label
  > `payment_requests.donation_label` · `bool` · opcional
- 🕒 vendor_notes
  > `payment_requests.vendor_notes` · `timestamp` · opcional
- 🔗 donation_id <!-- orbit link: "#donation_history", status: "clave foránea" -->
  > `payment_requests.donation_id` · `int8` · obligatoria
  >
  > Apunta a [`donation_history`](#donation_history).
- 🔢 order_notes
  > `payment_requests.order_notes` · `int4` · opcional
- 🔢 visit_code
  > `payment_requests.visit_code` · `int4` · opcional
- 🧩 hold_code
  > `payment_requests.hold_code` · `jsonb` · opcional
- 🔢 order_code
  > `payment_requests.order_code` · `int4` · obligatoria
- 🔗 events_id <!-- orbit link: "#events", status: "clave foránea" -->
  > `payment_requests.events_id` · `int8` · obligatoria
  >
  > Apunta a [`events`](#events).
- 🔗 payment_id <!-- orbit link: "#payment_requests", status: "clave foránea" -->
  > `payment_requests.payment_id` · `int8` · obligatoria
  >
  > Apunta a [`payment_requests`](#payment_requests).
- ☑️ member_active
  > `payment_requests.member_active` · `bool` · obligatoria
- 🔢 staff_state
  > `payment_requests.staff_state` · `int4` · opcional
- 🕒 budget_label
  > `payment_requests.budget_label` · `timestamp` · opcional
- 🔤 member_reviewed
  > `payment_requests.member_reviewed` · `varchar` · opcional
- 🔤 card_status
  > `payment_requests.card_status` · `varchar` · opcional
- 🔤 budget_language
  > `payment_requests.budget_language` · `varchar` · obligatoria
- 🧩 review_language
  > `payment_requests.review_language` · `jsonb` · opcional
- 🕒 event_external
  > `payment_requests.event_external` · `timestamp` · obligatoria
- 🔤 review_kind
  > `payment_requests.review_kind` · `varchar` · obligatoria
- 🕒 book_state
  > `payment_requests.book_state` · `timestamp` · opcional

### report_links
<!-- orbit
id: "report_links"
icon: "🗃️"
collapsed: true
-->
> `report_links` · tabla de Personal y accesos
>
> Una fila por informe, con 17 columnas y unas 417 mil filas.
>
> **Pertenece a** [`order_items`](#order_items) · [`audit_links`](#audit_links) · [`room_counts`](#room_counts)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `report_links.id` · `int8` · obligatoria
- ☑️ staff_public
  > `report_links.staff_public` · `bool` · opcional
- 🧩 card_region
  > `report_links.card_region` · `jsonb` · opcional
- 🕒 donation_status
  > `report_links.donation_status` · `timestamp` · obligatoria
- 🔗 report_id <!-- orbit link: "#report_queues", status: "clave foránea" -->
  > `report_links.report_id` · `int8` · obligatoria
  >
  > Apunta a [`report_queues`](#report_queues).
- 🕒 tag_state
  > `report_links.tag_state` · `timestamp` · opcional
- 🕒 review_language
  > `report_links.review_language` · `timestamp` · opcional
- 🔗 seriess_id <!-- orbit link: "#seriess", status: "clave foránea" -->
  > `report_links.seriess_id` · `int8` · obligatoria
  >
  > Apunta a [`seriess`](#seriess).
- 🔤 device_total
  > `report_links.device_total` · `varchar` · obligatoria
- ☑️ member_source
  > `report_links.member_source` · `bool` · opcional
- 🧩 loan_priority
  > `report_links.loan_priority` · `jsonb` · opcional
- 🧩 copy_amount
  > `report_links.copy_amount` · `jsonb` · opcional
- ☑️ book_source
  > `report_links.book_source` · `bool` · opcional
- 🔤 donation_language
  > `report_links.donation_language` · `varchar` · opcional
- 🧩 donation_region
  > `report_links.donation_region` · `jsonb` · obligatoria
- 🔤 copy_count
  > `report_links.copy_count` · `varchar` · obligatoria
- 🔗 donation_id <!-- orbit link: "#donation_links", status: "clave foránea" -->
  > `report_links.donation_id` · `int8` · obligatoria
  >
  > Apunta a [`donation_links`](#donation_links).

### member_history
<!-- orbit
id: "member_history"
icon: "📇"
collapsed: true
-->
> `member_history` · tabla de Personal y accesos
>
> Una fila por socio, con 23 columnas y unas 91 mil filas.
>
> **Pertenece a** [`staff_settings`](#staff_settings) · [`staff_counts`](#staff_counts) · [`vendor_history`](#vendor_history) · [`hold_links_2`](#hold_links_2)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `member_history.id` · `int8` · obligatoria
- 🔢 budget_notes
  > `member_history.budget_notes` · `int4` · obligatoria
- 🧩 staff_priority
  > `member_history.staff_priority` · `jsonb` · opcional
- 🕒 order_public
  > `member_history.order_public` · `timestamp` · obligatoria
- 🧩 review_amount
  > `member_history.review_amount` · `jsonb` · obligatoria
- 🕒 member_created
  > `member_history.member_created` · `timestamp` · opcional
- 🕒 report_amount
  > `member_history.report_amount` · `timestamp` · obligatoria
- 🧩 visit_language
  > `member_history.visit_language` · `jsonb` · obligatoria
- 🧩 copy_active
  > `member_history.copy_active` · `jsonb` · opcional
- 🕒 audit_status
  > `member_history.audit_status` · `timestamp` · obligatoria
- 🔤 repair_notes
  > `member_history.repair_notes` · `varchar` · opcional
- ☑️ order_total
  > `member_history.order_total` · `bool` · obligatoria
- ☑️ license_active
  > `member_history.license_active` · `bool` · opcional
- ☑️ card_reviewed
  > `member_history.card_reviewed` · `bool` · opcional
- 🕒 loan_active
  > `member_history.loan_active` · `timestamp` · obligatoria
- ☑️ loan_total
  > `member_history.loan_total` · `bool` · opcional
- 🧩 copy_region
  > `member_history.copy_region` · `jsonb` · obligatoria
- 🔤 audit_ended
  > `member_history.audit_ended` · `varchar` · obligatoria
- ☑️ payment_code
  > `member_history.payment_code` · `bool` · opcional
- 🧩 member_external
  > `member_history.member_external` · `jsonb` · opcional
- 🔤 review_format
  > `member_history.review_format` · `varchar` · opcional
- 🔤 ebook_source
  > `member_history.ebook_source` · `varchar` · obligatoria
- 🔤 branch_ended
  > `member_history.branch_ended` · `varchar` · opcional

### vendor_settings
<!-- orbit
id: "vendor_settings"
icon: "📇"
collapsed: true
-->
> `vendor_settings` · tabla de Personal y accesos
>
> Una fila por proveedor, con 10 columnas y unas 237 mil filas.
>
> **Pertenece a** [`fee_history`](#fee_history) · [`notice_counts_2`](#notice_counts_2) · [`shelfs`](#shelfs)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `vendor_settings.id` · `int8` · obligatoria
- 🔤 visit_updated
  > `vendor_settings.visit_updated` · `varchar` · obligatoria
- 🔗 audit_id <!-- orbit link: "#audit_queues", status: "clave foránea" -->
  > `vendor_settings.audit_id` · `int8` · obligatoria
  >
  > Apunta a [`audit_queues`](#audit_queues).
- 🔗 vendor_id <!-- orbit link: "#vendor_items", status: "clave foránea" -->
  > `vendor_settings.vendor_id` · `int8` · obligatoria
  >
  > Apunta a [`vendor_items`](#vendor_items).
- 🕒 role_archived
  > `vendor_settings.role_archived` · `timestamp` · opcional
- 🧩 vendor_reviewed
  > `vendor_settings.vendor_reviewed` · `jsonb` · obligatoria
- 🔗 copy_id <!-- orbit link: "#copy_items", status: "clave foránea" -->
  > `vendor_settings.copy_id` · `int8` · obligatoria
  >
  > Apunta a [`copy_items`](#copy_items).
- 🔗 budgets_id <!-- orbit link: "#budgets", status: "clave foránea" -->
  > `vendor_settings.budgets_id` · `int8` · obligatoria
  >
  > Apunta a [`budgets`](#budgets).
- 🔗 series_id <!-- orbit link: "#series_counts_2", status: "clave foránea" -->
  > `vendor_settings.series_id` · `int8` · obligatoria
  >
  > Apunta a [`series_counts_2`](#series_counts_2).
- 🧩 series_reviewed
  > `vendor_settings.series_reviewed` · `jsonb` · opcional

### copy_counts
<!-- orbit
id: "copy_counts"
icon: "📇"
collapsed: true
-->
> `copy_counts` · tabla de Personal y accesos
>
> Una fila por ejemplar, con 13 columnas y unas 414 mil filas.
>
> **Pertenece a** [`room_counts_2`](#room_counts_2)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `copy_counts.id` · `int8` · obligatoria
- 🕒 staff_public
  > `copy_counts.staff_public` · `timestamp` · obligatoria
- 🧩 author_archived
  > `copy_counts.author_archived` · `jsonb` · opcional
- 🕒 audit_archived
  > `copy_counts.audit_archived` · `timestamp` · opcional
- 🕒 ebook_position
  > `copy_counts.ebook_position` · `timestamp` · opcional
- 🔢 copy_notes
  > `copy_counts.copy_notes` · `int4` · opcional
- ☑️ ebook_kind
  > `copy_counts.ebook_kind` · `bool` · opcional
- 🔗 payment_id <!-- orbit link: "#payment_settings", status: "clave foránea" -->
  > `copy_counts.payment_id` · `int8` · obligatoria
  >
  > Apunta a [`payment_settings`](#payment_settings).
- 🔗 staff_id <!-- orbit link: "#staff_counts", status: "clave foránea" -->
  > `copy_counts.staff_id` · `int8` · obligatoria
  >
  > Apunta a [`staff_counts`](#staff_counts).
- 🔤 audit_label
  > `copy_counts.audit_label` · `varchar` · obligatoria
- 🧩 hold_title
  > `copy_counts.hold_title` · `jsonb` · opcional
- ☑️ series_count
  > `copy_counts.series_count` · `bool` · opcional
- 🔤 payment_archived
  > `copy_counts.payment_archived` · `varchar` · opcional

### order_logs
<!-- orbit
id: "order_logs"
icon: "🗂️"
collapsed: true
-->
> `order_logs` · tabla de Personal y accesos
>
> Una fila por pedido, con 21 columnas y unas 184 mil filas.
>
> **Pertenece a** [`hold_links_2`](#hold_links_2) · [`series_counts_2`](#series_counts_2) · [`audit_settings`](#audit_settings)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `order_logs.id` · `int8` · obligatoria
- 🕒 branch_updated
  > `order_logs.branch_updated` · `timestamp` · opcional
- 🕒 report_started
  > `order_logs.report_started` · `timestamp` · opcional
- 🧩 device_count
  > `order_logs.device_count` · `jsonb` · opcional
- 🔤 branch_amount
  > `order_logs.branch_amount` · `varchar` · obligatoria
- 🕒 donation_total
  > `order_logs.donation_total` · `timestamp` · obligatoria
- 🧩 book_amount
  > `order_logs.book_amount` · `jsonb` · opcional
- 🧩 fee_total
  > `order_logs.fee_total` · `jsonb` · obligatoria
- 🔢 donation_ended
  > `order_logs.donation_ended` · `int4` · opcional
- 🕒 staff_region
  > `order_logs.staff_region` · `timestamp` · obligatoria
- ☑️ budget_code
  > `order_logs.budget_code` · `bool` · obligatoria
- ☑️ copy_kind
  > `order_logs.copy_kind` · `bool` · obligatoria
- 🔢 tag_notes
  > `order_logs.tag_notes` · `int4` · obligatoria
- 🔤 audit_external
  > `order_logs.audit_external` · `varchar` · obligatoria
- 🕒 audit_title
  > `order_logs.audit_title` · `timestamp` · obligatoria
- 🧩 vendor_language
  > `order_logs.vendor_language` · `jsonb` · opcional
- 🔢 tag_total
  > `order_logs.tag_total` · `int4` · opcional
- 🕒 vendor_reviewed
  > `order_logs.vendor_reviewed` · `timestamp` · opcional
- 🔤 notice_language
  > `order_logs.notice_language` · `varchar` · opcional
- 🔗 vendor_id <!-- orbit link: "#vendor_settings", status: "clave foránea" -->
  > `order_logs.vendor_id` · `int8` · obligatoria
  >
  > Apunta a [`vendor_settings`](#vendor_settings).
- 🧩 tag_region
  > `order_logs.tag_region` · `jsonb` · opcional

### report_versions
<!-- orbit
id: "report_versions"
icon: "📇"
collapsed: true
-->
> `report_versions` · tabla de Personal y accesos
>
> Una fila por informe, con 7 columnas y unas 266 mil filas.
>
> **Pertenece a** [`series_links`](#series_links) · [`loan_counts`](#loan_counts) · [`series_counts_2`](#series_counts_2)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `report_versions.id` · `int8` · obligatoria
- 🔤 ebook_notes
  > `report_versions.ebook_notes` · `varchar` · obligatoria
- 🔤 staff_started
  > `report_versions.staff_started` · `varchar` · obligatoria
- 🧩 budget_source
  > `report_versions.budget_source` · `jsonb` · obligatoria
- 🕒 order_language
  > `report_versions.order_language` · `timestamp` · obligatoria
- ☑️ visit_title
  > `report_versions.visit_title` · `bool` · opcional
- 🧩 card_ended
  > `report_versions.card_ended` · `jsonb` · obligatoria

### holds_2
<!-- orbit
id: "holds_2"
icon: "🧷"
collapsed: true
-->
> `holds_2` · tabla de Personal y accesos
>
> Una fila por reserva, con 18 columnas y unas 340 mil filas.
>
> **Pertenece a** [`order_logs`](#order_logs) · [`report_queues_2`](#report_queues_2) · [`card_queues`](#card_queues) · [`tag_counts`](#tag_counts)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `holds_2.id` · `int8` · obligatoria
- 🔗 device_id <!-- orbit link: "#device_settings", status: "clave foránea" -->
  > `holds_2.device_id` · `int8` · obligatoria
  >
  > Apunta a [`device_settings`](#device_settings).
- 🧩 room_archived
  > `holds_2.room_archived` · `jsonb` · opcional
- ☑️ report_public
  > `holds_2.report_public` · `bool` · obligatoria
- 🔢 branch_format
  > `holds_2.branch_format` · `int4` · opcional
- 🔤 order_status
  > `holds_2.order_status` · `varchar` · opcional
- 🕒 donation_updated
  > `holds_2.donation_updated` · `timestamp` · opcional
- ☑️ shelf_kind
  > `holds_2.shelf_kind` · `bool` · opcional
- 🔗 copy_id <!-- orbit link: "#copy_items", status: "clave foránea" -->
  > `holds_2.copy_id` · `int8` · obligatoria
  >
  > Apunta a [`copy_items`](#copy_items).
- 🔢 payment_language
  > `holds_2.payment_language` · `int4` · opcional
- 🧩 member_source
  > `holds_2.member_source` · `jsonb` · opcional
- 🕒 room_external
  > `holds_2.room_external` · `timestamp` · obligatoria
- 🔢 shelf_active
  > `holds_2.shelf_active` · `int4` · opcional
- ☑️ payment_public
  > `holds_2.payment_public` · `bool` · obligatoria
- 🧩 shelf_total
  > `holds_2.shelf_total` · `jsonb` · obligatoria
- 🔤 donation_code
  > `holds_2.donation_code` · `varchar` · obligatoria
- 🔤 series_priority
  > `holds_2.series_priority` · `varchar` · opcional
- 🔗 vendor_id <!-- orbit link: "#vendor_settings", status: "clave foránea" -->
  > `holds_2.vendor_id` · `int8` · obligatoria
  >
  > Apunta a [`vendor_settings`](#vendor_settings).

### branchs
<!-- orbit
id: "branchs"
icon: "🗂️"
collapsed: true
-->
> `branchs` · tabla de Personal y accesos
>
> Una fila por sucursal, con 14 columnas y unas 619 mil filas.
>
> **Pertenece a** [`staff_counts`](#staff_counts) · [`staff_logs`](#staff_logs) · [`room_requests`](#room_requests)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `branchs.id` · `int8` · obligatoria
- 🕒 report_ended
  > `branchs.report_ended` · `timestamp` · obligatoria
- 🕒 staff_archived
  > `branchs.staff_archived` · `timestamp` · obligatoria
- 🔗 report_id <!-- orbit link: "#report_items", status: "clave foránea" -->
  > `branchs.report_id` · `int8` · obligatoria
  >
  > Apunta a [`report_items`](#report_items).
- 🔤 member_active
  > `branchs.member_active` · `varchar` · obligatoria
- 🔤 hold_source
  > `branchs.hold_source` · `varchar` · opcional
- 🔢 notice_source
  > `branchs.notice_source` · `int4` · obligatoria
- ☑️ hold_format
  > `branchs.hold_format` · `bool` · opcional
- ☑️ order_source
  > `branchs.order_source` · `bool` · obligatoria
- 🕒 copy_status
  > `branchs.copy_status` · `timestamp` · opcional
- 🔤 review_format
  > `branchs.review_format` · `varchar` · obligatoria
- 🔤 vendor_language
  > `branchs.vendor_language` · `varchar` · opcional
- 🧩 member_label
  > `branchs.member_label` · `jsonb` · opcional
- 🔤 series_total
  > `branchs.series_total` · `varchar` · opcional

### series_links
<!-- orbit
id: "series_links"
icon: "🗃️"
collapsed: true
-->
> `series_links` · tabla de Personal y accesos
>
> Una fila por serie, con 23 columnas y unas 436 mil filas.
>
> **Pertenece a** [`book_links`](#book_links)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `series_links.id` · `int8` · obligatoria
- 🔤 shelf_ended
  > `series_links.shelf_ended` · `varchar` · obligatoria
- ☑️ visit_position
  > `series_links.visit_position` · `bool` · opcional
- 🕒 report_priority
  > `series_links.report_priority` · `timestamp` · obligatoria
- 🧩 author_kind
  > `series_links.author_kind` · `jsonb` · opcional
- ☑️ donation_format
  > `series_links.donation_format` · `bool` · obligatoria
- 🔤 shelf_created
  > `series_links.shelf_created` · `varchar` · obligatoria
- 🧩 repair_public
  > `series_links.repair_public` · `jsonb` · opcional
- ☑️ event_source
  > `series_links.event_source` · `bool` · obligatoria
- ☑️ fee_count
  > `series_links.fee_count` · `bool` · obligatoria
- 🔤 notice_started
  > `series_links.notice_started` · `varchar` · opcional
- ☑️ vendor_position
  > `series_links.vendor_position` · `bool` · opcional
- 🕒 device_count
  > `series_links.device_count` · `timestamp` · obligatoria
- 🔗 donation_id <!-- orbit link: "#donation_links", status: "clave foránea" -->
  > `series_links.donation_id` · `int8` · obligatoria
  >
  > Apunta a [`donation_links`](#donation_links).
- 🕒 ebook_reviewed
  > `series_links.ebook_reviewed` · `timestamp` · opcional
- ☑️ license_kind
  > `series_links.license_kind` · `bool` · opcional
- 🔢 review_archived
  > `series_links.review_archived` · `int4` · obligatoria
- 🔢 member_status
  > `series_links.member_status` · `int4` · opcional
- ☑️ loan_source
  > `series_links.loan_source` · `bool` · obligatoria
- 🔗 member_id <!-- orbit link: "#member_versions", status: "clave foránea" -->
  > `series_links.member_id` · `int8` · obligatoria
  >
  > Apunta a [`member_versions`](#member_versions).
- 🔢 tag_public
  > `series_links.tag_public` · `int4` · opcional
- 🕒 book_region
  > `series_links.book_region` · `timestamp` · opcional
- 🔢 hold_position
  > `series_links.hold_position` · `int4` · obligatoria

## 📨 Avisos
<!-- orbit
id: "area-notices"
color: "#DB2777"
-->
> 10 tablas. Las más grandes: [`ebook_requests`](#ebook_requests), [`card_links_2`](#card_links_2), [`review_counts`](#review_counts).
>
> **Relacionada con** [`order_items`](#order_items) · [`hold_links_2`](#hold_links_2) · [`book_links`](#book_links)

### ebook_requests
<!-- orbit
id: "ebook_requests"
icon: "🗂️"
collapsed: true
-->
> `ebook_requests` · tabla de Avisos
>
> Una fila por libro electrónico, con 6 columnas y unas 437 mil filas.
>
> **Pertenece a** [`review_logs`](#review_logs) · [`card_links_2`](#card_links_2) · [`event_history`](#event_history) · [`audit_queues`](#audit_queues)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `ebook_requests.id` · `int8` · obligatoria
- 🔤 series_code
  > `ebook_requests.series_code` · `varchar` · opcional
- 🔗 branch_id <!-- orbit link: "#branch_links", status: "clave foránea" -->
  > `ebook_requests.branch_id` · `int8` · obligatoria
  >
  > Apunta a [`branch_links`](#branch_links).
- 🧩 fee_code
  > `ebook_requests.fee_code` · `jsonb` · obligatoria
- ☑️ member_region
  > `ebook_requests.member_region` · `bool` · obligatoria
- 🔗 visit_id <!-- orbit link: "#visit_items", status: "clave foránea" -->
  > `ebook_requests.visit_id` · `int8` · obligatoria
  >
  > Apunta a [`visit_items`](#visit_items).

### card_links_2
<!-- orbit
id: "card_links_2"
icon: "🧷"
collapsed: true
-->
> `card_links_2` · tabla de Avisos
>
> Una fila por credencial, con 13 columnas y unas 205 mil filas.
>
> **Pertenece a** [`ebook_logs`](#ebook_logs)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `card_links_2.id` · `int8` · obligatoria
- 🔗 review_id <!-- orbit link: "#review_links", status: "clave foránea" -->
  > `card_links_2.review_id` · `int8` · obligatoria
  >
  > Apunta a [`review_links`](#review_links).
- 🔢 ebook_language
  > `card_links_2.ebook_language` · `int4` · opcional
- 🕒 fee_archived
  > `card_links_2.fee_archived` · `timestamp` · obligatoria
- 🕒 loan_kind
  > `card_links_2.loan_kind` · `timestamp` · obligatoria
- 🕒 review_format
  > `card_links_2.review_format` · `timestamp` · obligatoria
- 🔢 order_kind
  > `card_links_2.order_kind` · `int4` · opcional
- 🔗 review_id_2 <!-- orbit link: "#review_counts", status: "clave foránea" -->
  > `card_links_2.review_id_2` · `int8` · obligatoria
  >
  > Apunta a [`review_counts`](#review_counts).
- ☑️ loan_kind_2
  > `card_links_2.loan_kind_2` · `bool` · obligatoria
- ☑️ member_notes
  > `card_links_2.member_notes` · `bool` · obligatoria
- ☑️ report_format
  > `card_links_2.report_format` · `bool` · obligatoria
- 🔤 fee_format
  > `card_links_2.fee_format` · `varchar` · obligatoria
- 🔗 payment_id <!-- orbit link: "#payment_settings", status: "clave foránea" -->
  > `card_links_2.payment_id` · `int8` · obligatoria
  >
  > Apunta a [`payment_settings`](#payment_settings).

### review_counts
<!-- orbit
id: "review_counts"
icon: "📗"
collapsed: true
-->
> `review_counts` · tabla de Avisos
>
> Una fila por reseña, con 16 columnas y unas 486 mil filas.
>
> **Pertenece a** [`payment_versions`](#payment_versions) · [`report_versions`](#report_versions)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `review_counts.id` · `int8` · obligatoria
- 🔤 book_notes
  > `review_counts.book_notes` · `varchar` · opcional
- 🕒 loan_public
  > `review_counts.loan_public` · `timestamp` · opcional
- 🧩 donation_title
  > `review_counts.donation_title` · `jsonb` · obligatoria
- ☑️ author_count
  > `review_counts.author_count` · `bool` · obligatoria
- ☑️ copy_reviewed
  > `review_counts.copy_reviewed` · `bool` · obligatoria
- 🔗 report_id <!-- orbit link: "#report_queues", status: "clave foránea" -->
  > `review_counts.report_id` · `int8` · obligatoria
  >
  > Apunta a [`report_queues`](#report_queues).
- ☑️ series_amount
  > `review_counts.series_amount` · `bool` · opcional
- 🔢 member_status
  > `review_counts.member_status` · `int4` · opcional
- 🔗 review_id <!-- orbit link: "#review_links", status: "clave foránea" -->
  > `review_counts.review_id` · `int8` · obligatoria
  >
  > Apunta a [`review_links`](#review_links).
- ☑️ loan_source
  > `review_counts.loan_source` · `bool` · opcional
- 🔤 donation_started
  > `review_counts.donation_started` · `varchar` · obligatoria
- 🔢 budget_external
  > `review_counts.budget_external` · `int4` · opcional
- 🔗 loan_id <!-- orbit link: "#loan_logs", status: "clave foránea" -->
  > `review_counts.loan_id` · `int8` · obligatoria
  >
  > Apunta a [`loan_logs`](#loan_logs).
- 🔤 author_archived
  > `review_counts.author_archived` · `varchar` · obligatoria
- 🧩 audit_reviewed
  > `review_counts.audit_reviewed` · `jsonb` · opcional

### vendor_counts
<!-- orbit
id: "vendor_counts"
icon: "🧮"
collapsed: true
-->
> `vendor_counts` · tabla de Avisos
>
> Una fila por proveedor, con 16 columnas y unas 78 mil filas.
>
> **Pertenece a** [`loan_settings`](#loan_settings)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `vendor_counts.id` · `int8` · obligatoria
- 🔢 room_format
  > `vendor_counts.room_format` · `int4` · obligatoria
- 🔤 budget_kind
  > `vendor_counts.budget_kind` · `varchar` · obligatoria
- 🧩 report_format
  > `vendor_counts.report_format` · `jsonb` · obligatoria
- 🔗 audit_id <!-- orbit link: "#audit_links_2", status: "clave foránea" -->
  > `vendor_counts.audit_id` · `int8` · obligatoria
  >
  > Apunta a [`audit_links_2`](#audit_links_2).
- ☑️ budget_count
  > `vendor_counts.budget_count` · `bool` · obligatoria
- ☑️ book_title
  > `vendor_counts.book_title` · `bool` · opcional
- 🔢 hold_amount
  > `vendor_counts.hold_amount` · `int4` · opcional
- 🕒 tag_archived
  > `vendor_counts.tag_archived` · `timestamp` · obligatoria
- 🔤 review_active
  > `vendor_counts.review_active` · `varchar` · obligatoria
- 🧩 review_count
  > `vendor_counts.review_count` · `jsonb` · obligatoria
- 🧩 repair_created
  > `vendor_counts.repair_created` · `jsonb` · obligatoria
- 🔗 series_id <!-- orbit link: "#series_counts", status: "clave foránea" -->
  > `vendor_counts.series_id` · `int8` · obligatoria
  >
  > Apunta a [`series_counts`](#series_counts).
- 🧩 notice_active
  > `vendor_counts.notice_active` · `jsonb` · obligatoria
- 🔢 role_reviewed
  > `vendor_counts.role_reviewed` · `int4` · obligatoria
- 🔗 budgets_id <!-- orbit link: "#budgets", status: "clave foránea" -->
  > `vendor_counts.budgets_id` · `int8` · obligatoria
  >
  > Apunta a [`budgets`](#budgets).

### staff_settings
<!-- orbit
id: "staff_settings"
icon: "🗃️"
collapsed: true
-->
> `staff_settings` · tabla de Avisos
>
> Una fila por empleado, con 20 columnas y unas 732 mil filas.
>
> **Pertenece a** [`ebook_requests`](#ebook_requests)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `staff_settings.id` · `int8` · obligatoria
- 🔢 tag_kind
  > `staff_settings.tag_kind` · `int4` · obligatoria
- 🧩 vendor_external
  > `staff_settings.vendor_external` · `jsonb` · obligatoria
- 🕒 report_external
  > `staff_settings.report_external` · `timestamp` · obligatoria
- 🔗 audit_id <!-- orbit link: "#audit_links_2", status: "clave foránea" -->
  > `staff_settings.audit_id` · `int8` · obligatoria
  >
  > Apunta a [`audit_links_2`](#audit_links_2).
- ☑️ device_created
  > `staff_settings.device_created` · `bool` · opcional
- 🔢 device_state
  > `staff_settings.device_state` · `int4` · obligatoria
- 🕒 card_language
  > `staff_settings.card_language` · `timestamp` · opcional
- ☑️ member_title
  > `staff_settings.member_title` · `bool` · obligatoria
- 🔤 author_count
  > `staff_settings.author_count` · `varchar` · obligatoria
- ☑️ order_state
  > `staff_settings.order_state` · `bool` · obligatoria
- 🧩 payment_notes
  > `staff_settings.payment_notes` · `jsonb` · obligatoria
- 🕒 book_region
  > `staff_settings.book_region` · `timestamp` · obligatoria
- 🧩 report_reviewed
  > `staff_settings.report_reviewed` · `jsonb` · opcional
- 🕒 donation_ended
  > `staff_settings.donation_ended` · `timestamp` · opcional
- 🕒 repair_title
  > `staff_settings.repair_title` · `timestamp` · opcional
- 🕒 branch_ended
  > `staff_settings.branch_ended` · `timestamp` · opcional
- 🔤 room_count
  > `staff_settings.room_count` · `varchar` · obligatoria
- 🧩 license_position
  > `staff_settings.license_position` · `jsonb` · obligatoria
- 🔢 author_position
  > `staff_settings.author_position` · `int4` · obligatoria

### visit_queues
<!-- orbit
id: "visit_queues"
icon: "🗂️"
collapsed: true
-->
> `visit_queues` · tabla de Avisos
>
> Una fila por visita, con 18 columnas y unas 48 mil filas.
>
> **Pertenece a** [`report_items`](#report_items) · [`shelf_counts`](#shelf_counts) · [`card_queues`](#card_queues) · [`device_settings`](#device_settings)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `visit_queues.id` · `int8` · obligatoria
- ☑️ event_state
  > `visit_queues.event_state` · `bool` · opcional
- 🧩 tag_label
  > `visit_queues.tag_label` · `jsonb` · obligatoria
- 🧩 license_state
  > `visit_queues.license_state` · `jsonb` · obligatoria
- 🔤 report_active
  > `visit_queues.report_active` · `varchar` · opcional
- 🕒 book_amount
  > `visit_queues.book_amount` · `timestamp` · opcional
- 🕒 payment_kind
  > `visit_queues.payment_kind` · `timestamp` · obligatoria
- ☑️ visit_created
  > `visit_queues.visit_created` · `bool` · obligatoria
- 🕒 tag_format
  > `visit_queues.tag_format` · `timestamp` · obligatoria
- 🔤 donation_name
  > `visit_queues.donation_name` · `varchar` · obligatoria
- 🔤 visit_notes
  > `visit_queues.visit_notes` · `varchar` · obligatoria
- ☑️ order_count
  > `visit_queues.order_count` · `bool` · obligatoria
- 🧩 device_position
  > `visit_queues.device_position` · `jsonb` · opcional
- 🕒 notice_code
  > `visit_queues.notice_code` · `timestamp` · obligatoria
- 🔢 device_state
  > `visit_queues.device_state` · `int4` · opcional
- 🧩 report_label
  > `visit_queues.report_label` · `jsonb` · opcional
- 🕒 device_count
  > `visit_queues.device_count` · `timestamp` · opcional
- 🔢 repair_state
  > `visit_queues.repair_state` · `int4` · opcional

### loan_logs_2
<!-- orbit
id: "loan_logs_2"
icon: "🗂️"
collapsed: true
-->
> `loan_logs_2` · tabla de Avisos
>
> Una fila por préstamo, con 24 columnas y unas 9 mil filas.
>
> **Pertenece a** [`tag_settings`](#tag_settings) · [`series_links`](#series_links) · [`shelf_counts`](#shelf_counts)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `loan_logs_2.id` · `int8` · obligatoria
- 🔗 card_id <!-- orbit link: "#card_queues", status: "clave foránea" -->
  > `loan_logs_2.card_id` · `int8` · obligatoria
  >
  > Apunta a [`card_queues`](#card_queues).
- ☑️ shelf_region
  > `loan_logs_2.shelf_region` · `bool` · opcional
- 🕒 order_source
  > `loan_logs_2.order_source` · `timestamp` · opcional
- ☑️ series_name
  > `loan_logs_2.series_name` · `bool` · obligatoria
- 🕒 order_code
  > `loan_logs_2.order_code` · `timestamp` · obligatoria
- ☑️ role_amount
  > `loan_logs_2.role_amount` · `bool` · obligatoria
- 🧩 review_public
  > `loan_logs_2.review_public` · `jsonb` · obligatoria
- 🔤 loan_kind
  > `loan_logs_2.loan_kind` · `varchar` · opcional
- 🔗 card_id_2 <!-- orbit link: "#card_links", status: "clave foránea" -->
  > `loan_logs_2.card_id_2` · `int8` · obligatoria
  >
  > Apunta a [`card_links`](#card_links).
- 🧩 visit_active
  > `loan_logs_2.visit_active` · `jsonb` · opcional
- 🧩 fee_active
  > `loan_logs_2.fee_active` · `jsonb` · opcional
- 🔗 card_id_3 <!-- orbit link: "#card_links", status: "clave foránea" -->
  > `loan_logs_2.card_id_3` · `int8` · obligatoria
  >
  > Apunta a [`card_links`](#card_links).
- 🧩 vendor_language
  > `loan_logs_2.vendor_language` · `jsonb` · obligatoria
- 🧩 shelf_count
  > `loan_logs_2.shelf_count` · `jsonb` · obligatoria
- 🔗 loan_id <!-- orbit link: "#loan_counts", status: "clave foránea" -->
  > `loan_logs_2.loan_id` · `int8` · obligatoria
  >
  > Apunta a [`loan_counts`](#loan_counts).
- 🧩 author_kind
  > `loan_logs_2.author_kind` · `jsonb` · opcional
- 🧩 copy_ended
  > `loan_logs_2.copy_ended` · `jsonb` · obligatoria
- ☑️ member_title
  > `loan_logs_2.member_title` · `bool` · obligatoria
- 🧩 review_code
  > `loan_logs_2.review_code` · `jsonb` · obligatoria
- 🔗 licenses_id <!-- orbit link: "#licenses", status: "clave foránea" -->
  > `loan_logs_2.licenses_id` · `int8` · obligatoria
  >
  > Apunta a [`licenses`](#licenses).
- 🕒 card_format
  > `loan_logs_2.card_format` · `timestamp` · opcional
- 🔤 room_position
  > `loan_logs_2.room_position` · `varchar` · opcional
- ☑️ visit_amount
  > `loan_logs_2.visit_amount` · `bool` · obligatoria

### donations
<!-- orbit
id: "donations"
icon: "🗂️"
collapsed: true
-->
> `donations` · tabla de Avisos
>
> Una fila por donación, con 19 columnas y unas 377 mil filas.
>
> **Pertenece a** [`budget_settings`](#budget_settings)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `donations.id` · `int8` · obligatoria
- 🔤 series_source
  > `donations.series_source` · `varchar` · opcional
- ☑️ ebook_name
  > `donations.ebook_name` · `bool` · obligatoria
- 🔗 repair_id <!-- orbit link: "#repair_requests", status: "clave foránea" -->
  > `donations.repair_id` · `int8` · obligatoria
  >
  > Apunta a [`repair_requests`](#repair_requests).
- 🔤 member_name
  > `donations.member_name` · `varchar` · opcional
- 🔤 audit_total
  > `donations.audit_total` · `varchar` · obligatoria
- ☑️ staff_name
  > `donations.staff_name` · `bool` · opcional
- 🔤 hold_name
  > `donations.hold_name` · `varchar` · obligatoria
- 🔢 staff_external
  > `donations.staff_external` · `int4` · obligatoria
- 🔢 report_started
  > `donations.report_started` · `int4` · opcional
- 🕒 ebook_amount
  > `donations.ebook_amount` · `timestamp` · opcional
- 🔤 ebook_active
  > `donations.ebook_active` · `varchar` · opcional
- 🕒 shelf_name
  > `donations.shelf_name` · `timestamp` · obligatoria
- 🔗 vendor_id <!-- orbit link: "#vendor_history", status: "clave foránea" -->
  > `donations.vendor_id` · `int8` · obligatoria
  >
  > Apunta a [`vendor_history`](#vendor_history).
- 🕒 review_updated
  > `donations.review_updated` · `timestamp` · opcional
- ☑️ role_state
  > `donations.role_state` · `bool` · opcional
- 🔗 staff_id <!-- orbit link: "#staff_counts", status: "clave foránea" -->
  > `donations.staff_id` · `int8` · obligatoria
  >
  > Apunta a [`staff_counts`](#staff_counts).
- 🕒 vendor_label
  > `donations.vendor_label` · `timestamp` · opcional
- 🔢 notice_position
  > `donations.notice_position` · `int4` · opcional

### review_logs
<!-- orbit
id: "review_logs"
icon: "🧷"
collapsed: true
-->
> `review_logs` · tabla de Avisos
>
> Una fila por reseña, con 7 columnas y unas 143 mil filas.
>
> **Pertenece a** [`copy_versions`](#copy_versions) · [`review_counts`](#review_counts) · [`donation_history`](#donation_history) · [`tags`](#tags)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `review_logs.id` · `int8` · obligatoria
- 🧩 license_position
  > `review_logs.license_position` · `jsonb` · opcional
- 🧩 staff_count
  > `review_logs.staff_count` · `jsonb` · opcional
- ☑️ review_total
  > `review_logs.review_total` · `bool` · opcional
- 🔗 staff_id <!-- orbit link: "#staff_items", status: "clave foránea" -->
  > `review_logs.staff_id` · `int8` · obligatoria
  >
  > Apunta a [`staff_items`](#staff_items).
- 🔗 report_id <!-- orbit link: "#report_items", status: "clave foránea" -->
  > `review_logs.report_id` · `int8` · obligatoria
  >
  > Apunta a [`report_items`](#report_items).
- 🔤 copy_source
  > `review_logs.copy_source` · `varchar` · obligatoria

### license_settings
<!-- orbit
id: "license_settings"
icon: "🗂️"
collapsed: true
-->
> `license_settings` · tabla de Avisos
>
> Una fila por licencia, con 10 columnas y unas 325 mil filas.
>
> **Pertenece a** [`ebook_queues`](#ebook_queues)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `license_settings.id` · `int8` · obligatoria
- 🕒 notice_created
  > `license_settings.notice_created` · `timestamp` · opcional
- ☑️ author_created
  > `license_settings.author_created` · `bool` · opcional
- 🔗 vendor_id <!-- orbit link: "#vendor_items", status: "clave foránea" -->
  > `license_settings.vendor_id` · `int8` · obligatoria
  >
  > Apunta a [`vendor_items`](#vendor_items).
- 🕒 loan_source
  > `license_settings.loan_source` · `timestamp` · obligatoria
- 🔗 seriess_id <!-- orbit link: "#seriess", status: "clave foránea" -->
  > `license_settings.seriess_id` · `int8` · obligatoria
  >
  > Apunta a [`seriess`](#seriess).
- 🕒 notice_updated
  > `license_settings.notice_updated` · `timestamp` · obligatoria
- 🔤 repair_created
  > `license_settings.repair_created` · `varchar` · obligatoria
- 🕒 order_kind
  > `license_settings.order_kind` · `timestamp` · opcional
- 🔗 license_id <!-- orbit link: "#license_logs_2", status: "clave foránea" -->
  > `license_settings.license_id` · `int8` · obligatoria
  >
  > Apunta a [`license_logs_2`](#license_logs_2).

## 🌐 Préstamo digital
<!-- orbit
id: "area-digital-lending"
color: "#059669"
-->
> 10 tablas. Las más grandes: [`budgets`](#budgets), [`licenses`](#licenses), [`audit_links_4`](#audit_links_4).
>
> **Relacionada con** [`rooms`](#rooms) · [`license_logs`](#license_logs) · [`budget_settings`](#budget_settings)

### budgets
<!-- orbit
id: "budgets"
icon: "🧮"
collapsed: true
-->
> `budgets` · tabla de Préstamo digital
>
> Una fila por presupuesto, con 7 columnas y unas 227 mil filas.
>
> **Pertenece a** [`card_links_2`](#card_links_2) · [`ebook_logs`](#ebook_logs) · [`series_counts`](#series_counts)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `budgets.id` · `int8` · obligatoria
- ☑️ notice_updated
  > `budgets.notice_updated` · `bool` · obligatoria
- 🕒 author_ended
  > `budgets.author_ended` · `timestamp` · opcional
- 🕒 loan_language
  > `budgets.loan_language` · `timestamp` · opcional
- 🔗 vendor_id <!-- orbit link: "#vendor_counts", status: "clave foránea" -->
  > `budgets.vendor_id` · `int8` · obligatoria
  >
  > Apunta a [`vendor_counts`](#vendor_counts).
- ☑️ visit_source
  > `budgets.visit_source` · `bool` · obligatoria
- 🔢 staff_count
  > `budgets.staff_count` · `int4` · obligatoria

### licenses
<!-- orbit
id: "licenses"
icon: "🗃️"
collapsed: true
-->
> `licenses` · tabla de Préstamo digital
>
> Una fila por licencia, con 19 columnas y unas 709 mil filas.
>
> **Pertenece a** [`visits`](#visits) · [`hold_links`](#hold_links)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `licenses.id` · `int8` · obligatoria
- ☑️ order_state
  > `licenses.order_state` · `bool` · obligatoria
- 🔢 review_title
  > `licenses.review_title` · `int4` · obligatoria
- 🔢 hold_kind
  > `licenses.hold_kind` · `int4` · opcional
- ☑️ report_code
  > `licenses.report_code` · `bool` · obligatoria
- 🔗 events_id <!-- orbit link: "#events", status: "clave foránea" -->
  > `licenses.events_id` · `int8` · obligatoria
  >
  > Apunta a [`events`](#events).
- 🕒 repair_status
  > `licenses.repair_status` · `timestamp` · opcional
- ☑️ series_started
  > `licenses.series_started` · `bool` · opcional
- 🧩 copy_priority
  > `licenses.copy_priority` · `jsonb` · obligatoria
- 🔗 loan_id <!-- orbit link: "#loan_logs", status: "clave foránea" -->
  > `licenses.loan_id` · `int8` · obligatoria
  >
  > Apunta a [`loan_logs`](#loan_logs).
- 🔤 budget_language
  > `licenses.budget_language` · `varchar` · obligatoria
- ☑️ notice_archived
  > `licenses.notice_archived` · `bool` · opcional
- 🕒 budget_archived
  > `licenses.budget_archived` · `timestamp` · obligatoria
- 🔗 budgets_id <!-- orbit link: "#budgets", status: "clave foránea" -->
  > `licenses.budgets_id` · `int8` · obligatoria
  >
  > Apunta a [`budgets`](#budgets).
- 🕒 tag_kind
  > `licenses.tag_kind` · `timestamp` · opcional
- 🔤 order_external
  > `licenses.order_external` · `varchar` · obligatoria
- 🧩 role_started
  > `licenses.role_started` · `jsonb` · opcional
- 🔤 event_state
  > `licenses.event_state` · `varchar` · opcional
- 🔢 license_notes
  > `licenses.license_notes` · `int4` · obligatoria

### audit_links_4
<!-- orbit
id: "audit_links_4"
icon: "🧷"
collapsed: true
-->
> `audit_links_4` · tabla de Préstamo digital
>
> Una fila por auditoría, con 24 columnas y unas 461 mil filas.
>
> **Pertenece a** [`member_counts_2`](#member_counts_2) · [`report_queues`](#report_queues)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `audit_links_4.id` · `int8` · obligatoria
- 🔢 copy_public
  > `audit_links_4.copy_public` · `int4` · opcional
- 🔢 hold_started
  > `audit_links_4.hold_started` · `int4` · opcional
- 🕒 role_title
  > `audit_links_4.role_title` · `timestamp` · obligatoria
- 🧩 series_code
  > `audit_links_4.series_code` · `jsonb` · opcional
- ☑️ role_kind
  > `audit_links_4.role_kind` · `bool` · opcional
- 🧩 visit_updated
  > `audit_links_4.visit_updated` · `jsonb` · obligatoria
- 🔗 series_id <!-- orbit link: "#series_requests", status: "clave foránea" -->
  > `audit_links_4.series_id` · `int8` · obligatoria
  >
  > Apunta a [`series_requests`](#series_requests).
- 🧩 tag_code
  > `audit_links_4.tag_code` · `jsonb` · obligatoria
- 🧩 donation_updated
  > `audit_links_4.donation_updated` · `jsonb` · opcional
- 🧩 vendor_external
  > `audit_links_4.vendor_external` · `jsonb` · obligatoria
- 🧩 event_archived
  > `audit_links_4.event_archived` · `jsonb` · opcional
- 🔤 notice_code
  > `audit_links_4.notice_code` · `varchar` · obligatoria
- 🔤 budget_priority
  > `audit_links_4.budget_priority` · `varchar` · opcional
- ☑️ staff_state
  > `audit_links_4.staff_state` · `bool` · obligatoria
- 🔢 donation_code
  > `audit_links_4.donation_code` · `int4` · opcional
- ☑️ notice_kind
  > `audit_links_4.notice_kind` · `bool` · obligatoria
- 🔗 room_id <!-- orbit link: "#room_counts_2", status: "clave foránea" -->
  > `audit_links_4.room_id` · `int8` · obligatoria
  >
  > Apunta a [`room_counts_2`](#room_counts_2).
- 🔤 shelf_active
  > `audit_links_4.shelf_active` · `varchar` · obligatoria
- ☑️ order_language
  > `audit_links_4.order_language` · `bool` · obligatoria
- 🕒 ebook_ended
  > `audit_links_4.ebook_ended` · `timestamp` · opcional
- 🔢 staff_amount
  > `audit_links_4.staff_amount` · `int4` · obligatoria
- 🕒 book_language
  > `audit_links_4.book_language` · `timestamp` · opcional
- 🔗 branch_id <!-- orbit link: "#branch_links", status: "clave foránea" -->
  > `audit_links_4.branch_id` · `int8` · obligatoria
  >
  > Apunta a [`branch_links`](#branch_links).

### room_counts_2
<!-- orbit
id: "room_counts_2"
icon: "📇"
collapsed: true
-->
> `room_counts_2` · tabla de Préstamo digital
>
> Una fila por sala, con 9 columnas y unas 509 mil filas.
>
> **Pertenece a** [`report_logs`](#report_logs)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `room_counts_2.id` · `int8` · obligatoria
- 🔗 author_id <!-- orbit link: "#author_links", status: "clave foránea" -->
  > `room_counts_2.author_id` · `int8` · obligatoria
  >
  > Apunta a [`author_links`](#author_links).
- 🕒 visit_status
  > `room_counts_2.visit_status` · `timestamp` · opcional
- 🕒 branch_format
  > `room_counts_2.branch_format` · `timestamp` · opcional
- ☑️ series_ended
  > `room_counts_2.series_ended` · `bool` · opcional
- 🔗 loan_id <!-- orbit link: "#loan_settings", status: "clave foránea" -->
  > `room_counts_2.loan_id` · `int8` · obligatoria
  >
  > Apunta a [`loan_settings`](#loan_settings).
- 🕒 review_public
  > `room_counts_2.review_public` · `timestamp` · obligatoria
- 🔗 copy_id <!-- orbit link: "#copy_items", status: "clave foránea" -->
  > `room_counts_2.copy_id` · `int8` · obligatoria
  >
  > Apunta a [`copy_items`](#copy_items).
- 🔢 device_format
  > `room_counts_2.device_format` · `int4` · opcional

### notice_counts_2
<!-- orbit
id: "notice_counts_2"
icon: "🧷"
collapsed: true
-->
> `notice_counts_2` · tabla de Préstamo digital
>
> Una fila por aviso, con 15 columnas y unas 588 mil filas.
>
> **Pertenece a** [`tags`](#tags) · [`payment_requests`](#payment_requests)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `notice_counts_2.id` · `int8` · obligatoria
- 🔢 event_priority
  > `notice_counts_2.event_priority` · `int4` · obligatoria
- 🕒 staff_status
  > `notice_counts_2.staff_status` · `timestamp` · opcional
- 🔗 donation_id <!-- orbit link: "#donation_history", status: "clave foránea" -->
  > `notice_counts_2.donation_id` · `int8` · obligatoria
  >
  > Apunta a [`donation_history`](#donation_history).
- ☑️ repair_created
  > `notice_counts_2.repair_created` · `bool` · obligatoria
- 🔢 order_label
  > `notice_counts_2.order_label` · `int4` · obligatoria
- 🔤 author_title
  > `notice_counts_2.author_title` · `varchar` · opcional
- 🕒 room_format
  > `notice_counts_2.room_format` · `timestamp` · opcional
- 🧩 review_status
  > `notice_counts_2.review_status` · `jsonb` · opcional
- 🔗 review_id <!-- orbit link: "#review_links", status: "clave foránea" -->
  > `notice_counts_2.review_id` · `int8` · obligatoria
  >
  > Apunta a [`review_links`](#review_links).
- ☑️ license_external
  > `notice_counts_2.license_external` · `bool` · opcional
- ☑️ report_region
  > `notice_counts_2.report_region` · `bool` · opcional
- 🔢 hold_code
  > `notice_counts_2.hold_code` · `int4` · obligatoria
- 🔤 vendor_title
  > `notice_counts_2.vendor_title` · `varchar` · opcional
- 🔗 report_id <!-- orbit link: "#report_versions", status: "clave foránea" -->
  > `notice_counts_2.report_id` · `int8` · obligatoria
  >
  > Apunta a [`report_versions`](#report_versions).

### room_settings
<!-- orbit
id: "room_settings"
icon: "🧮"
collapsed: true
-->
> `room_settings` · tabla de Préstamo digital
>
> Una fila por sala, con 9 columnas y unas 519 mil filas.
>
> **Pertenece a** [`report_logs`](#report_logs) · [`author_links`](#author_links) · [`author_history`](#author_history) · [`holds_2`](#holds_2)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `room_settings.id` · `int8` · obligatoria
- 🔢 author_language
  > `room_settings.author_language` · `int4` · obligatoria
- 🔤 book_total
  > `room_settings.book_total` · `varchar` · opcional
- 🧩 license_position
  > `room_settings.license_position` · `jsonb` · obligatoria
- 🔢 repair_source
  > `room_settings.repair_source` · `int4` · obligatoria
- 🕒 hold_priority
  > `room_settings.hold_priority` · `timestamp` · obligatoria
- 🔤 staff_region
  > `room_settings.staff_region` · `varchar` · opcional
- 🔢 order_position
  > `room_settings.order_position` · `int4` · obligatoria
- 🔤 ebook_notes
  > `room_settings.ebook_notes` · `varchar` · opcional

### room_logs
<!-- orbit
id: "room_logs"
icon: "🧷"
collapsed: true
-->
> `room_logs` · tabla de Préstamo digital
>
> Una fila por sala, con 20 columnas y unas 679 mil filas.
>
> **Pertenece a** [`shelf_queues`](#shelf_queues) · [`audit_links_2`](#audit_links_2) · [`event_queues`](#event_queues)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `room_logs.id` · `int8` · obligatoria
- 🔢 repair_source
  > `room_logs.repair_source` · `int4` · opcional
- 🔗 member_id <!-- orbit link: "#member_history", status: "clave foránea" -->
  > `room_logs.member_id` · `int8` · obligatoria
  >
  > Apunta a [`member_history`](#member_history).
- 🔢 audit_archived
  > `room_logs.audit_archived` · `int4` · opcional
- 🔗 copy_id <!-- orbit link: "#copy_history", status: "clave foránea" -->
  > `room_logs.copy_id` · `int8` · obligatoria
  >
  > Apunta a [`copy_history`](#copy_history).
- ☑️ author_label
  > `room_logs.author_label` · `bool` · opcional
- 🔗 review_id <!-- orbit link: "#review_logs", status: "clave foránea" -->
  > `room_logs.review_id` · `int8` · obligatoria
  >
  > Apunta a [`review_logs`](#review_logs).
- ☑️ shelf_label
  > `room_logs.shelf_label` · `bool` · opcional
- 🕒 budget_reviewed
  > `room_logs.budget_reviewed` · `timestamp` · obligatoria
- 🔢 repair_external
  > `room_logs.repair_external` · `int4` · obligatoria
- 🕒 card_code
  > `room_logs.card_code` · `timestamp` · opcional
- 🔤 book_amount
  > `room_logs.book_amount` · `varchar` · opcional
- ☑️ tag_public
  > `room_logs.tag_public` · `bool` · opcional
- 🕒 report_total
  > `room_logs.report_total` · `timestamp` · obligatoria
- 🧩 payment_status
  > `room_logs.payment_status` · `jsonb` · obligatoria
- 🔗 room_id <!-- orbit link: "#room_counts", status: "clave foránea" -->
  > `room_logs.room_id` · `int8` · obligatoria
  >
  > Apunta a [`room_counts`](#room_counts).
- 🔤 order_started
  > `room_logs.order_started` · `varchar` · opcional
- 🔤 donation_updated
  > `room_logs.donation_updated` · `varchar` · obligatoria
- 🔢 notice_ended
  > `room_logs.notice_ended` · `int4` · opcional
- 🔗 visit_id <!-- orbit link: "#visit_items", status: "clave foránea" -->
  > `room_logs.visit_id` · `int8` · obligatoria
  >
  > Apunta a [`visit_items`](#visit_items).

### seriess
<!-- orbit
id: "seriess"
icon: "🧮"
collapsed: true
-->
> `seriess` · tabla de Préstamo digital
>
> Una fila por serie, con 11 columnas y unas 228 mil filas.
>
> **Pertenece a** [`author_history`](#author_history) · [`role_counts`](#role_counts) · [`notice_counts_2`](#notice_counts_2)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `seriess.id` · `int8` · obligatoria
- 🔢 notice_kind
  > `seriess.notice_kind` · `int4` · obligatoria
- 🔤 visit_state
  > `seriess.visit_state` · `varchar` · obligatoria
- ☑️ review_amount
  > `seriess.review_amount` · `bool` · opcional
- ☑️ ebook_started
  > `seriess.ebook_started` · `bool` · opcional
- 🕒 branch_source
  > `seriess.branch_source` · `timestamp` · opcional
- 🕒 author_archived
  > `seriess.author_archived` · `timestamp` · opcional
- 🔢 branch_state
  > `seriess.branch_state` · `int4` · opcional
- 🧩 room_position
  > `seriess.room_position` · `jsonb` · opcional
- 🔤 series_title
  > `seriess.series_title` · `varchar` · opcional
- ☑️ loan_external
  > `seriess.loan_external` · `bool` · obligatoria

### visit_links
<!-- orbit
id: "visit_links"
icon: "🧮"
collapsed: true
-->
> `visit_links` · tabla de Préstamo digital
>
> Una fila por visita, con 19 columnas y unas 376 mil filas.
>
> **Pertenece a** [`review_logs`](#review_logs) · [`audit_links_2`](#audit_links_2) · [`card_logs`](#card_logs) · [`review_versions`](#review_versions)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `visit_links.id` · `int8` · obligatoria
- 🔢 visit_position
  > `visit_links.visit_position` · `int4` · obligatoria
- 🔤 device_notes
  > `visit_links.device_notes` · `varchar` · opcional
- 🧩 notice_label
  > `visit_links.notice_label` · `jsonb` · opcional
- 🔗 audit_id <!-- orbit link: "#audit_links_3", status: "clave foránea" -->
  > `visit_links.audit_id` · `int8` · obligatoria
  >
  > Apunta a [`audit_links_3`](#audit_links_3).
- 🔢 role_name
  > `visit_links.role_name` · `int4` · opcional
- 🕒 card_created
  > `visit_links.card_created` · `timestamp` · opcional
- 🧩 hold_kind
  > `visit_links.hold_kind` · `jsonb` · opcional
- 🔗 loan_id <!-- orbit link: "#loan_logs_2", status: "clave foránea" -->
  > `visit_links.loan_id` · `int8` · obligatoria
  >
  > Apunta a [`loan_logs_2`](#loan_logs_2).
- 🧩 shelf_format
  > `visit_links.shelf_format` · `jsonb` · obligatoria
- ☑️ role_source
  > `visit_links.role_source` · `bool` · opcional
- 🧩 repair_code
  > `visit_links.repair_code` · `jsonb` · obligatoria
- 🔤 report_ended
  > `visit_links.report_ended` · `varchar` · opcional
- 🔤 card_format
  > `visit_links.card_format` · `varchar` · opcional
- ☑️ event_name
  > `visit_links.event_name` · `bool` · opcional
- 🕒 room_total
  > `visit_links.room_total` · `timestamp` · obligatoria
- 🔤 event_priority
  > `visit_links.event_priority` · `varchar` · obligatoria
- ☑️ license_archived
  > `visit_links.license_archived` · `bool` · obligatoria
- 🔤 budget_source
  > `visit_links.budget_source` · `varchar` · opcional

### book_items
<!-- orbit
id: "book_items"
icon: "📇"
collapsed: true
-->
> `book_items` · tabla de Préstamo digital
>
> Una fila por libro, con 8 columnas y unas 399 mil filas.
>
> **Pertenece a** [`author_history`](#author_history) · [`donation_history`](#donation_history)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `book_items.id` · `int8` · obligatoria
- 🕒 staff_active
  > `book_items.staff_active` · `timestamp` · opcional
- 🔗 licenses_id <!-- orbit link: "#licenses", status: "clave foránea" -->
  > `book_items.licenses_id` · `int8` · obligatoria
  >
  > Apunta a [`licenses`](#licenses).
- ☑️ fee_ended
  > `book_items.fee_ended` · `bool` · obligatoria
- 🧩 vendor_name
  > `book_items.vendor_name` · `jsonb` · opcional
- 🔤 donation_count
  > `book_items.donation_count` · `varchar` · obligatoria
- 🔗 tag_id <!-- orbit link: "#tag_settings", status: "clave foránea" -->
  > `book_items.tag_id` · `int8` · obligatoria
  >
  > Apunta a [`tag_settings`](#tag_settings).
- 🧩 audit_status
  > `book_items.audit_status` · `jsonb` · obligatoria

## 🧰 Mantenimiento
<!-- orbit
id: "area-maintenance"
color: "#B45309"
-->
> 10 tablas. Las más grandes: [`loan_history`](#loan_history), [`book_settings`](#book_settings), [`copy_history`](#copy_history).
>
> **Relacionada con** [`vendor_counts`](#vendor_counts) · [`vendor_history`](#vendor_history) · [`role_counts`](#role_counts)

### loan_history
<!-- orbit
id: "loan_history"
icon: "🧮"
collapsed: true
-->
> `loan_history` · tabla de Mantenimiento
>
> Una fila por préstamo, con 11 columnas y unas 728 mil filas.
>
> **Pertenece a** [`author_links_2`](#author_links_2)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `loan_history.id` · `int8` · obligatoria
- 🔗 copy_id <!-- orbit link: "#copy_versions", status: "clave foránea" -->
  > `loan_history.copy_id` · `int8` · obligatoria
  >
  > Apunta a [`copy_versions`](#copy_versions).
- 🧩 member_region
  > `loan_history.member_region` · `jsonb` · opcional
- 🕒 budget_title
  > `loan_history.budget_title` · `timestamp` · obligatoria
- ☑️ report_updated
  > `loan_history.report_updated` · `bool` · opcional
- 🔤 author_position
  > `loan_history.author_position` · `varchar` · obligatoria
- 🔢 loan_position
  > `loan_history.loan_position` · `int4` · obligatoria
- 🔢 shelf_created
  > `loan_history.shelf_created` · `int4` · opcional
- 🔤 notice_state
  > `loan_history.notice_state` · `varchar` · opcional
- 🔗 staff_id <!-- orbit link: "#staff_settings", status: "clave foránea" -->
  > `loan_history.staff_id` · `int8` · obligatoria
  >
  > Apunta a [`staff_settings`](#staff_settings).
- ☑️ audit_kind
  > `loan_history.audit_kind` · `bool` · opcional

### book_settings
<!-- orbit
id: "book_settings"
icon: "🗃️"
collapsed: true
-->
> `book_settings` · tabla de Mantenimiento
>
> Una fila por libro, con 12 columnas y unas 113 mil filas.
>
> **Pertenece a** [`audit_links_3`](#audit_links_3) · [`review_queues`](#review_queues)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `book_settings.id` · `int8` · obligatoria
- 🔢 book_source
  > `book_settings.book_source` · `int4` · opcional
- ☑️ report_source
  > `book_settings.report_source` · `bool` · opcional
- 🔗 ebook_id <!-- orbit link: "#ebook_logs", status: "clave foránea" -->
  > `book_settings.ebook_id` · `int8` · obligatoria
  >
  > Apunta a [`ebook_logs`](#ebook_logs).
- 🔗 budget_id <!-- orbit link: "#budget_queues", status: "clave foránea" -->
  > `book_settings.budget_id` · `int8` · obligatoria
  >
  > Apunta a [`budget_queues`](#budget_queues).
- 🔤 series_format
  > `book_settings.series_format` · `varchar` · obligatoria
- 🧩 shelf_label
  > `book_settings.shelf_label` · `jsonb` · obligatoria
- 🕒 copy_region
  > `book_settings.copy_region` · `timestamp` · obligatoria
- 🧩 repair_ended
  > `book_settings.repair_ended` · `jsonb` · obligatoria
- 🔤 tag_source
  > `book_settings.tag_source` · `varchar` · opcional
- 🔤 ebook_priority
  > `book_settings.ebook_priority` · `varchar` · obligatoria
- 🔢 visit_code
  > `book_settings.visit_code` · `int4` · obligatoria

### copy_history
<!-- orbit
id: "copy_history"
icon: "📇"
collapsed: true
-->
> `copy_history` · tabla de Mantenimiento
>
> Una fila por ejemplar, con 20 columnas y unas 547 mil filas.
>
> **Pertenece a** [`staff_counts`](#staff_counts) · [`series_counts_2`](#series_counts_2) · [`tag_settings`](#tag_settings) · [`budget_settings_2`](#budget_settings_2)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `copy_history.id` · `int8` · obligatoria
- 🔢 hold_total
  > `copy_history.hold_total` · `int4` · opcional
- ☑️ staff_kind
  > `copy_history.staff_kind` · `bool` · obligatoria
- 🔗 device_id <!-- orbit link: "#device_settings", status: "clave foránea" -->
  > `copy_history.device_id` · `int8` · obligatoria
  >
  > Apunta a [`device_settings`](#device_settings).
- 🔢 audit_external
  > `copy_history.audit_external` · `int4` · obligatoria
- 🧩 room_status
  > `copy_history.room_status` · `jsonb` · opcional
- 🕒 audit_code
  > `copy_history.audit_code` · `timestamp` · obligatoria
- ☑️ staff_total
  > `copy_history.staff_total` · `bool` · obligatoria
- 🧩 budget_language
  > `copy_history.budget_language` · `jsonb` · opcional
- 🔤 vendor_kind
  > `copy_history.vendor_kind` · `varchar` · obligatoria
- 🔢 role_ended
  > `copy_history.role_ended` · `int4` · opcional
- 🔤 staff_archived
  > `copy_history.staff_archived` · `varchar` · opcional
- 🔗 hold_id <!-- orbit link: "#hold_history", status: "clave foránea" -->
  > `copy_history.hold_id` · `int8` · obligatoria
  >
  > Apunta a [`hold_history`](#hold_history).
- 🔗 series_id <!-- orbit link: "#series_requests", status: "clave foránea" -->
  > `copy_history.series_id` · `int8` · obligatoria
  >
  > Apunta a [`series_requests`](#series_requests).
- 🔗 series_id_2 <!-- orbit link: "#series_history", status: "clave foránea" -->
  > `copy_history.series_id_2` · `int8` · obligatoria
  >
  > Apunta a [`series_history`](#series_history).
- 🔗 license_id <!-- orbit link: "#license_logs_2", status: "clave foránea" -->
  > `copy_history.license_id` · `int8` · obligatoria
  >
  > Apunta a [`license_logs_2`](#license_logs_2).
- 🔢 staff_archived_2
  > `copy_history.staff_archived_2` · `int4` · opcional
- 🧩 device_language
  > `copy_history.device_language` · `jsonb` · opcional
- 🔗 donation_id <!-- orbit link: "#donation_requests", status: "clave foránea" -->
  > `copy_history.donation_id` · `int8` · obligatoria
  >
  > Apunta a [`donation_requests`](#donation_requests).
- 🕒 device_format
  > `copy_history.device_format` · `timestamp` · opcional

### report_counts
<!-- orbit
id: "report_counts"
icon: "📇"
collapsed: true
-->
> `report_counts` · tabla de Mantenimiento
>
> Una fila por informe, con 24 columnas y unas 542 mil filas.
>
> **Pertenece a** [`order_links`](#order_links) · [`loan_history`](#loan_history) · [`review_items`](#review_items) · [`notice_counts_2`](#notice_counts_2)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `report_counts.id` · `int8` · obligatoria
- 🕒 shelf_updated
  > `report_counts.shelf_updated` · `timestamp` · obligatoria
- 🔤 order_started
  > `report_counts.order_started` · `varchar` · opcional
- 🔢 notice_status
  > `report_counts.notice_status` · `int4` · obligatoria
- ☑️ budget_language
  > `report_counts.budget_language` · `bool` · obligatoria
- 🔤 donation_kind
  > `report_counts.donation_kind` · `varchar` · opcional
- 🔗 member_id <!-- orbit link: "#member_versions", status: "clave foránea" -->
  > `report_counts.member_id` · `int8` · obligatoria
  >
  > Apunta a [`member_versions`](#member_versions).
- 🔗 donation_id <!-- orbit link: "#donation_queues", status: "clave foránea" -->
  > `report_counts.donation_id` · `int8` · obligatoria
  >
  > Apunta a [`donation_queues`](#donation_queues).
- 🔗 room_id <!-- orbit link: "#room_counts_2", status: "clave foránea" -->
  > `report_counts.room_id` · `int8` · obligatoria
  >
  > Apunta a [`room_counts_2`](#room_counts_2).
- ☑️ book_code
  > `report_counts.book_code` · `bool` · obligatoria
- 🕒 ebook_started
  > `report_counts.ebook_started` · `timestamp` · obligatoria
- ☑️ visit_ended
  > `report_counts.visit_ended` · `bool` · obligatoria
- 🕒 room_code
  > `report_counts.room_code` · `timestamp` · obligatoria
- 🔗 loan_id <!-- orbit link: "#loan_settings", status: "clave foránea" -->
  > `report_counts.loan_id` · `int8` · obligatoria
  >
  > Apunta a [`loan_settings`](#loan_settings).
- 🕒 repair_position
  > `report_counts.repair_position` · `timestamp` · opcional
- 🕒 staff_total
  > `report_counts.staff_total` · `timestamp` · opcional
- 🔤 fee_format
  > `report_counts.fee_format` · `varchar` · obligatoria
- 🔤 repair_title
  > `report_counts.repair_title` · `varchar` · obligatoria
- 🕒 ebook_kind
  > `report_counts.ebook_kind` · `timestamp` · obligatoria
- ☑️ payment_state
  > `report_counts.payment_state` · `bool` · opcional
- ☑️ shelf_public
  > `report_counts.shelf_public` · `bool` · obligatoria
- 🔤 fee_priority
  > `report_counts.fee_priority` · `varchar` · obligatoria
- 🔗 loan_id_2 <!-- orbit link: "#loan_logs_2", status: "clave foránea" -->
  > `report_counts.loan_id_2` · `int8` · obligatoria
  >
  > Apunta a [`loan_logs_2`](#loan_logs_2).
- 🔢 staff_kind
  > `report_counts.staff_kind` · `int4` · opcional

### event_queues
<!-- orbit
id: "event_queues"
icon: "🧮"
collapsed: true
-->
> `event_queues` · tabla de Mantenimiento
>
> Una fila por evento, con 21 columnas y unas 403 mil filas.
>
> **Pertenece a** [`loan_counts`](#loan_counts) · [`license_logs_2`](#license_logs_2) · [`notice_counts`](#notice_counts) · [`donation_links`](#donation_links)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `event_queues.id` · `int8` · obligatoria
- 🔢 role_count
  > `event_queues.role_count` · `int4` · obligatoria
- 🔢 donation_format
  > `event_queues.donation_format` · `int4` · opcional
- 🕒 room_position
  > `event_queues.room_position` · `timestamp` · obligatoria
- ☑️ review_position
  > `event_queues.review_position` · `bool` · obligatoria
- 🧩 budget_count
  > `event_queues.budget_count` · `jsonb` · obligatoria
- 🕒 ebook_updated
  > `event_queues.ebook_updated` · `timestamp` · opcional
- 🔢 notice_label
  > `event_queues.notice_label` · `int4` · obligatoria
- 🕒 review_position_2
  > `event_queues.review_position_2` · `timestamp` · opcional
- 🔢 repair_active
  > `event_queues.repair_active` · `int4` · obligatoria
- 🔢 role_code
  > `event_queues.role_code` · `int4` · opcional
- 🔗 hold_id <!-- orbit link: "#hold_links_2", status: "clave foránea" -->
  > `event_queues.hold_id` · `int8` · obligatoria
  >
  > Apunta a [`hold_links_2`](#hold_links_2).
- 🔗 audit_id <!-- orbit link: "#audit_links", status: "clave foránea" -->
  > `event_queues.audit_id` · `int8` · obligatoria
  >
  > Apunta a [`audit_links`](#audit_links).
- 🔤 room_archived
  > `event_queues.room_archived` · `varchar` · obligatoria
- ☑️ hold_state
  > `event_queues.hold_state` · `bool` · opcional
- 🔤 review_name
  > `event_queues.review_name` · `varchar` · opcional
- 🔢 visit_source
  > `event_queues.visit_source` · `int4` · opcional
- 🔤 ebook_started
  > `event_queues.ebook_started` · `varchar` · opcional
- 🔢 donation_count
  > `event_queues.donation_count` · `int4` · obligatoria
- ☑️ event_title
  > `event_queues.event_title` · `bool` · obligatoria
- 🔢 audit_region
  > `event_queues.audit_region` · `int4` · opcional

### budget_settings_2
<!-- orbit
id: "budget_settings_2"
icon: "🧷"
collapsed: true
-->
> `budget_settings_2` · tabla de Mantenimiento
>
> Una fila por presupuesto, con 18 columnas y unas 799 mil filas.
>
> **Pertenece a** [`license_versions`](#license_versions) · [`book_links`](#book_links)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `budget_settings_2.id` · `int8` · obligatoria
- ☑️ author_total
  > `budget_settings_2.author_total` · `bool` · opcional
- 🔗 order_id <!-- orbit link: "#order_links", status: "clave foránea" -->
  > `budget_settings_2.order_id` · `int8` · obligatoria
  >
  > Apunta a [`order_links`](#order_links).
- 🧩 vendor_source
  > `budget_settings_2.vendor_source` · `jsonb` · obligatoria
- 🕒 device_public
  > `budget_settings_2.device_public` · `timestamp` · opcional
- 🔤 series_priority
  > `budget_settings_2.series_priority` · `varchar` · obligatoria
- 🔢 author_priority
  > `budget_settings_2.author_priority` · `int4` · obligatoria
- 🕒 budget_amount
  > `budget_settings_2.budget_amount` · `timestamp` · obligatoria
- 🕒 visit_label
  > `budget_settings_2.visit_label` · `timestamp` · obligatoria
- 🔗 visit_id <!-- orbit link: "#visit_items", status: "clave foránea" -->
  > `budget_settings_2.visit_id` · `int8` · obligatoria
  >
  > Apunta a [`visit_items`](#visit_items).
- 🕒 audit_region
  > `budget_settings_2.audit_region` · `timestamp` · obligatoria
- 🔤 room_total
  > `budget_settings_2.room_total` · `varchar` · opcional
- 🔗 vendor_id <!-- orbit link: "#vendor_settings", status: "clave foránea" -->
  > `budget_settings_2.vendor_id` · `int8` · obligatoria
  >
  > Apunta a [`vendor_settings`](#vendor_settings).
- 🧩 notice_position
  > `budget_settings_2.notice_position` · `jsonb` · obligatoria
- 🔢 report_code
  > `budget_settings_2.report_code` · `int4` · obligatoria
- 🧩 repair_language
  > `budget_settings_2.repair_language` · `jsonb` · opcional
- 🔢 series_created
  > `budget_settings_2.series_created` · `int4` · obligatoria
- 🕒 loan_source
  > `budget_settings_2.loan_source` · `timestamp` · obligatoria

### series_counts_2
<!-- orbit
id: "series_counts_2"
icon: "📇"
collapsed: true
-->
> `series_counts_2` · tabla de Mantenimiento
>
> Una fila por serie, con 11 columnas y unas 263 mil filas.
>
> **Pertenece a** [`loan_counts`](#loan_counts) · [`branchs`](#branchs) · [`visit_items`](#visit_items) · [`holds_2`](#holds_2)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `series_counts_2.id` · `int8` · obligatoria
- 🔗 member_id <!-- orbit link: "#member_counts_2", status: "clave foránea" -->
  > `series_counts_2.member_id` · `int8` · obligatoria
  >
  > Apunta a [`member_counts_2`](#member_counts_2).
- 🔤 hold_archived
  > `series_counts_2.hold_archived` · `varchar` · obligatoria
- ☑️ notice_position
  > `series_counts_2.notice_position` · `bool` · obligatoria
- 🔤 fee_archived
  > `series_counts_2.fee_archived` · `varchar` · opcional
- 🔤 card_language
  > `series_counts_2.card_language` · `varchar` · opcional
- ☑️ series_title
  > `series_counts_2.series_title` · `bool` · opcional
- 🔗 tag_id <!-- orbit link: "#tag_counts", status: "clave foránea" -->
  > `series_counts_2.tag_id` · `int8` · obligatoria
  >
  > Apunta a [`tag_counts`](#tag_counts).
- 🔤 device_format
  > `series_counts_2.device_format` · `varchar` · opcional
- 🧩 hold_source
  > `series_counts_2.hold_source` · `jsonb` · obligatoria
- 🔗 license_id <!-- orbit link: "#license_settings", status: "clave foránea" -->
  > `series_counts_2.license_id` · `int8` · obligatoria
  >
  > Apunta a [`license_settings`](#license_settings).

### copy_links
<!-- orbit
id: "copy_links"
icon: "🗃️"
collapsed: true
-->
> `copy_links` · tabla de Mantenimiento
>
> Una fila por ejemplar, con 16 columnas y unas 493 mil filas.
>
> **Pertenece a** [`ebook_requests`](#ebook_requests)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `copy_links.id` · `int8` · obligatoria
- 🧩 book_title
  > `copy_links.book_title` · `jsonb` · obligatoria
- ☑️ audit_status
  > `copy_links.audit_status` · `bool` · opcional
- 🔢 hold_position
  > `copy_links.hold_position` · `int4` · opcional
- 🔢 member_active
  > `copy_links.member_active` · `int4` · opcional
- 🕒 loan_title
  > `copy_links.loan_title` · `timestamp` · obligatoria
- 🕒 branch_notes
  > `copy_links.branch_notes` · `timestamp` · opcional
- 🕒 visit_archived
  > `copy_links.visit_archived` · `timestamp` · opcional
- 🕒 series_updated
  > `copy_links.series_updated` · `timestamp` · opcional
- 🔤 review_archived
  > `copy_links.review_archived` · `varchar` · opcional
- 🕒 notice_updated
  > `copy_links.notice_updated` · `timestamp` · obligatoria
- 🧩 fee_code
  > `copy_links.fee_code` · `jsonb` · opcional
- 🔤 member_state
  > `copy_links.member_state` · `varchar` · opcional
- 🔗 member_id <!-- orbit link: "#member_counts_2", status: "clave foránea" -->
  > `copy_links.member_id` · `int8` · obligatoria
  >
  > Apunta a [`member_counts_2`](#member_counts_2).
- 🕒 copy_state
  > `copy_links.copy_state` · `timestamp` · obligatoria
- 🔢 branch_started
  > `copy_links.branch_started` · `int4` · obligatoria

### payment_settings
<!-- orbit
id: "payment_settings"
icon: "🧷"
collapsed: true
-->
> `payment_settings` · tabla de Mantenimiento
>
> Una fila por pago, con 23 columnas y unas 871 mil filas.
>
> **Pertenece a** [`report_links`](#report_links) · [`copy_items`](#copy_items)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `payment_settings.id` · `int8` · obligatoria
- 🔗 ebook_id <!-- orbit link: "#ebook_requests", status: "clave foránea" -->
  > `payment_settings.ebook_id` · `int8` · obligatoria
  >
  > Apunta a [`ebook_requests`](#ebook_requests).
- ☑️ notice_reviewed
  > `payment_settings.notice_reviewed` · `bool` · opcional
- 🧩 hold_count
  > `payment_settings.hold_count` · `jsonb` · opcional
- 🧩 hold_format
  > `payment_settings.hold_format` · `jsonb` · opcional
- 🕒 hold_public
  > `payment_settings.hold_public` · `timestamp` · obligatoria
- 🔤 tag_notes
  > `payment_settings.tag_notes` · `varchar` · obligatoria
- 🕒 budget_title
  > `payment_settings.budget_title` · `timestamp` · obligatoria
- 🔤 report_archived
  > `payment_settings.report_archived` · `varchar` · opcional
- 🔢 audit_position
  > `payment_settings.audit_position` · `int4` · opcional
- 🔤 shelf_region
  > `payment_settings.shelf_region` · `varchar` · opcional
- ☑️ vendor_updated
  > `payment_settings.vendor_updated` · `bool` · obligatoria
- 🕒 audit_count
  > `payment_settings.audit_count` · `timestamp` · obligatoria
- 🕒 copy_region
  > `payment_settings.copy_region` · `timestamp` · opcional
- 🧩 card_ended
  > `payment_settings.card_ended` · `jsonb` · opcional
- ☑️ tag_amount
  > `payment_settings.tag_amount` · `bool` · obligatoria
- 🧩 audit_notes
  > `payment_settings.audit_notes` · `jsonb` · opcional
- 🧩 hold_ended
  > `payment_settings.hold_ended` · `jsonb` · obligatoria
- 🧩 branch_code
  > `payment_settings.branch_code` · `jsonb` · opcional
- 🕒 visit_notes
  > `payment_settings.visit_notes` · `timestamp` · opcional
- 🧩 branch_started
  > `payment_settings.branch_started` · `jsonb` · opcional
- 🔗 event_id <!-- orbit link: "#event_history", status: "clave foránea" -->
  > `payment_settings.event_id` · `int8` · obligatoria
  >
  > Apunta a [`event_history`](#event_history).
- ☑️ book_position
  > `payment_settings.book_position` · `bool` · opcional

### book_links
<!-- orbit
id: "book_links"
icon: "🗂️"
collapsed: true
-->
> `book_links` · tabla de Mantenimiento
>
> Una fila por libro, con 14 columnas y unas 223 mil filas.
>
> **Pertenece a** [`budget_queues`](#budget_queues) · [`series_counts`](#series_counts)

- 🔑 id <!-- orbit status: "clave primaria" -->
  > `book_links.id` · `int8` · obligatoria
- ☑️ copy_archived
  > `book_links.copy_archived` · `bool` · opcional
- 🔗 event_id <!-- orbit link: "#event_history", status: "clave foránea" -->
  > `book_links.event_id` · `int8` · obligatoria
  >
  > Apunta a [`event_history`](#event_history).
- 🧩 payment_amount
  > `book_links.payment_amount` · `jsonb` · opcional
- 🕒 report_title
  > `book_links.report_title` · `timestamp` · obligatoria
- 🧩 copy_name
  > `book_links.copy_name` · `jsonb` · obligatoria
- 🧩 license_public
  > `book_links.license_public` · `jsonb` · opcional
- ☑️ budget_title
  > `book_links.budget_title` · `bool` · obligatoria
- 🔤 event_amount
  > `book_links.event_amount` · `varchar` · opcional
- 🧩 license_position
  > `book_links.license_position` · `jsonb` · obligatoria
- 🧩 repair_priority
  > `book_links.repair_priority` · `jsonb` · obligatoria
- 🔤 room_label
  > `book_links.room_label` · `varchar` · obligatoria
- 🔢 staff_ended
  > `book_links.staff_ended` · `int4` · opcional
- ☑️ role_public
  > `book_links.role_public` · `bool` · opcional

