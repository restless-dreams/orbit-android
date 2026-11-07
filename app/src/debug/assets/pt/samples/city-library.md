---
title: A biblioteca municipal
theme: cosmos
---
# Biblioteca municipal
<!-- orbit
id: "library"
-->
> O banco de dados inventado de uma biblioteca municipal, desenhado como um sistema solar: **124 tabelas** em **12 áreas**. Cada planeta é uma área; voe até uma para ver as tabelas dela, e depois até uma tabela para ver as colunas.

## 📚 Catálogo
<!-- orbit
id: "area-catalog"
color: "#C2304A"
-->
> 10 tabelas. As maiores: [`copy_versions`](#copy_versions), [`fee_history`](#fee_history), [`license_logs`](#license_logs).
>
> **Relacionada a** [`author_links`](#author_links) · [`member_counts_2`](#member_counts_2) · [`shelfs`](#shelfs)

### copy_versions
<!-- orbit
id: "copy_versions"
icon: "📇"
collapsed: true
-->
> `copy_versions` · tabela de Catálogo
>
> Uma linha por exemplar, com 18 colunas e cerca de 454 mil linhas.
>
> **Pertence a** [`staff_settings`](#staff_settings) · [`card_links_2`](#card_links_2) · [`review_counts`](#review_counts)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `copy_versions.id` · `int8` · obrigatória
- 🧩 donation_started
  > `copy_versions.donation_started` · `jsonb` · opcional
- 🧩 event_format
  > `copy_versions.event_format` · `jsonb` · opcional
- 🔢 room_source
  > `copy_versions.room_source` · `int4` · obrigatória
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
- 🔗 series_id <!-- orbit link: "#series_history", status: "chave estrangeira" -->
  > `copy_versions.series_id` · `int8` · obrigatória
  >
  > Aponta para [`series_history`](#series_history).
- 🔤 staff_position
  > `copy_versions.staff_position` · `varchar` · opcional
- 🔢 ebook_created
  > `copy_versions.ebook_created` · `int4` · opcional
- 🔗 tag_id <!-- orbit link: "#tag_settings", status: "chave estrangeira" -->
  > `copy_versions.tag_id` · `int8` · obrigatória
  >
  > Aponta para [`tag_settings`](#tag_settings).
- 🧩 member_external
  > `copy_versions.member_external` · `jsonb` · obrigatória
- ☑️ event_external
  > `copy_versions.event_external` · `bool` · obrigatória
- 🔗 donation_id <!-- orbit link: "#donation_queues", status: "chave estrangeira" -->
  > `copy_versions.donation_id` · `int8` · obrigatória
  >
  > Aponta para [`donation_queues`](#donation_queues).
- ☑️ card_priority
  > `copy_versions.card_priority` · `bool` · obrigatória
- ☑️ vendor_notes
  > `copy_versions.vendor_notes` · `bool` · opcional

### fee_history
<!-- orbit
id: "fee_history"
icon: "🗃️"
collapsed: true
-->
> `fee_history` · tabela de Catálogo
>
> Uma linha por multa, com 9 colunas e cerca de 203 mil linhas.
>
> **Pertence a** [`report_logs`](#report_logs) · [`shelfs`](#shelfs) · [`device_items`](#device_items)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `fee_history.id` · `int8` · obrigatória
- 🔤 payment_notes
  > `fee_history.payment_notes` · `varchar` · obrigatória
- 🔢 ebook_region
  > `fee_history.ebook_region` · `int4` · opcional
- ☑️ order_total
  > `fee_history.order_total` · `bool` · obrigatória
- 🔤 staff_updated
  > `fee_history.staff_updated` · `varchar` · opcional
- 🔗 hold_id <!-- orbit link: "#hold_links", status: "chave estrangeira" -->
  > `fee_history.hold_id` · `int8` · obrigatória
  >
  > Aponta para [`hold_links`](#hold_links).
- 🔗 tags_id <!-- orbit link: "#tags", status: "chave estrangeira" -->
  > `fee_history.tags_id` · `int8` · obrigatória
  >
  > Aponta para [`tags`](#tags).
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
> `license_logs` · tabela de Catálogo
>
> Uma linha por licença, com 7 colunas e cerca de 729 mil linhas.
>
> **Pertence a** [`branch_links`](#branch_links)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `license_logs.id` · `int8` · obrigatória
- 🔗 ebook_id <!-- orbit link: "#ebook_versions", status: "chave estrangeira" -->
  > `license_logs.ebook_id` · `int8` · obrigatória
  >
  > Aponta para [`ebook_versions`](#ebook_versions).
- 🕒 ebook_count
  > `license_logs.ebook_count` · `timestamp` · opcional
- 🕒 ebook_reviewed
  > `license_logs.ebook_reviewed` · `timestamp` · obrigatória
- 🔤 budget_external
  > `license_logs.budget_external` · `varchar` · obrigatória
- 🔢 role_external
  > `license_logs.role_external` · `int4` · obrigatória
- 🔗 hold_id <!-- orbit link: "#hold_logs", status: "chave estrangeira" -->
  > `license_logs.hold_id` · `int8` · obrigatória
  >
  > Aponta para [`hold_logs`](#hold_logs).

### vendor_items
<!-- orbit
id: "vendor_items"
icon: "🗃️"
collapsed: true
-->
> `vendor_items` · tabela de Catálogo
>
> Uma linha por fornecedor, com 11 colunas e cerca de 336 mil linhas.
>
> **Pertence a** [`budget_settings_2`](#budget_settings_2) · [`vendor_counts`](#vendor_counts) · [`card_logs`](#card_logs) · [`fee_requests`](#fee_requests)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `vendor_items.id` · `int8` · obrigatória
- 🔗 author_id <!-- orbit link: "#author_history", status: "chave estrangeira" -->
  > `vendor_items.author_id` · `int8` · obrigatória
  >
  > Aponta para [`author_history`](#author_history).
- 🔤 card_state
  > `vendor_items.card_state` · `varchar` · opcional
- 🔤 notice_region
  > `vendor_items.notice_region` · `varchar` · opcional
- 🔗 loan_id <!-- orbit link: "#loan_history", status: "chave estrangeira" -->
  > `vendor_items.loan_id` · `int8` · obrigatória
  >
  > Aponta para [`loan_history`](#loan_history).
- 🧩 license_language
  > `vendor_items.license_language` · `jsonb` · obrigatória
- 🕒 donation_external
  > `vendor_items.donation_external` · `timestamp` · obrigatória
- 🔗 review_id <!-- orbit link: "#review_versions", status: "chave estrangeira" -->
  > `vendor_items.review_id` · `int8` · obrigatória
  >
  > Aponta para [`review_versions`](#review_versions).
- 🔗 license_id <!-- orbit link: "#license_logs", status: "chave estrangeira" -->
  > `vendor_items.license_id` · `int8` · obrigatória
  >
  > Aponta para [`license_logs`](#license_logs).
- 🕒 member_kind
  > `vendor_items.member_kind` · `timestamp` · obrigatória
- 🔤 budget_active
  > `vendor_items.budget_active` · `varchar` · obrigatória

### report_logs
<!-- orbit
id: "report_logs"
icon: "🗃️"
collapsed: true
-->
> `report_logs` · tabela de Catálogo
>
> Uma linha por relatório, com 24 colunas e cerca de 452 mil linhas.
>
> **Pertence a** [`hold_links`](#hold_links) · [`visits`](#visits)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `report_logs.id` · `int8` · obrigatória
- 🧩 review_name
  > `report_logs.review_name` · `jsonb` · opcional
- 🧩 repair_status
  > `report_logs.repair_status` · `jsonb` · obrigatória
- 🔤 vendor_name
  > `report_logs.vendor_name` · `varchar` · obrigatória
- 🧩 loan_code
  > `report_logs.loan_code` · `jsonb` · opcional
- 🧩 payment_language
  > `report_logs.payment_language` · `jsonb` · opcional
- 🔢 book_status
  > `report_logs.book_status` · `int4` · obrigatória
- 🧩 tag_position
  > `report_logs.tag_position` · `jsonb` · opcional
- 🕒 repair_count
  > `report_logs.repair_count` · `timestamp` · obrigatória
- 🕒 loan_code_2
  > `report_logs.loan_code_2` · `timestamp` · obrigatória
- ☑️ book_label
  > `report_logs.book_label` · `bool` · opcional
- ☑️ repair_language
  > `report_logs.repair_language` · `bool` · opcional
- 🔢 tag_amount
  > `report_logs.tag_amount` · `int4` · opcional
- 🔢 member_external
  > `report_logs.member_external` · `int4` · obrigatória
- 🔗 review_id <!-- orbit link: "#review_items", status: "chave estrangeira" -->
  > `report_logs.review_id` · `int8` · obrigatória
  >
  > Aponta para [`review_items`](#review_items).
- 🔤 review_status
  > `report_logs.review_status` · `varchar` · opcional
- 🔢 report_title
  > `report_logs.report_title` · `int4` · obrigatória
- 🔗 holds_id <!-- orbit link: "#holds_2", status: "chave estrangeira" -->
  > `report_logs.holds_id` · `int8` · obrigatória
  >
  > Aponta para [`holds_2`](#holds_2).
- 🔤 author_reviewed
  > `report_logs.author_reviewed` · `varchar` · opcional
- 🔤 order_count
  > `report_logs.order_count` · `varchar` · obrigatória
- 🧩 series_public
  > `report_logs.series_public` · `jsonb` · obrigatória
- ☑️ visit_language
  > `report_logs.visit_language` · `bool` · obrigatória
- 🕒 staff_reviewed
  > `report_logs.staff_reviewed` · `timestamp` · opcional
- 🕒 role_total
  > `report_logs.role_total` · `timestamp` · obrigatória

### visit_items
<!-- orbit
id: "visit_items"
icon: "📇"
collapsed: true
-->
> `visit_items` · tabela de Catálogo
>
> Uma linha por visita, com 7 colunas e cerca de 234 mil linhas.
>
> **Pertence a** [`donations`](#donations) · [`holds`](#holds) · [`visits`](#visits) · [`shelf_counts`](#shelf_counts)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `visit_items.id` · `int8` · obrigatória
- 🔢 fee_region
  > `visit_items.fee_region` · `int4` · opcional
- ☑️ device_total
  > `visit_items.device_total` · `bool` · opcional
- 🔗 notice_id <!-- orbit link: "#notice_counts", status: "chave estrangeira" -->
  > `visit_items.notice_id` · `int8` · obrigatória
  >
  > Aponta para [`notice_counts`](#notice_counts).
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
> `author_links` · tabela de Catálogo
>
> Uma linha por autor, com 19 colunas e cerca de 399 mil linhas.
>
> **Pertence a** [`report_queues_2`](#report_queues_2) · [`staff_counts`](#staff_counts) · [`audit_queues`](#audit_queues)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `author_links.id` · `int8` · obrigatória
- 🔗 staff_id <!-- orbit link: "#staff_counts", status: "chave estrangeira" -->
  > `author_links.staff_id` · `int8` · obrigatória
  >
  > Aponta para [`staff_counts`](#staff_counts).
- ☑️ license_state
  > `author_links.license_state` · `bool` · obrigatória
- 🕒 budget_code
  > `author_links.budget_code` · `timestamp` · opcional
- 🔗 series_id <!-- orbit link: "#series_links", status: "chave estrangeira" -->
  > `author_links.series_id` · `int8` · obrigatória
  >
  > Aponta para [`series_links`](#series_links).
- 🔤 tag_total
  > `author_links.tag_total` · `varchar` · opcional
- 🔢 staff_state
  > `author_links.staff_state` · `int4` · obrigatória
- ☑️ report_format
  > `author_links.report_format` · `bool` · obrigatória
- 🔗 audit_id <!-- orbit link: "#audit_queues", status: "chave estrangeira" -->
  > `author_links.audit_id` · `int8` · obrigatória
  >
  > Aponta para [`audit_queues`](#audit_queues).
- 🔗 copy_id <!-- orbit link: "#copy_items", status: "chave estrangeira" -->
  > `author_links.copy_id` · `int8` · obrigatória
  >
  > Aponta para [`copy_items`](#copy_items).
- 🔢 book_created
  > `author_links.book_created` · `int4` · opcional
- ☑️ audit_name
  > `author_links.audit_name` · `bool` · opcional
- 🔤 visit_public
  > `author_links.visit_public` · `varchar` · obrigatória
- 🔗 payment_id <!-- orbit link: "#payment_settings", status: "chave estrangeira" -->
  > `author_links.payment_id` · `int8` · obrigatória
  >
  > Aponta para [`payment_settings`](#payment_settings).
- 🕒 license_archived
  > `author_links.license_archived` · `timestamp` · obrigatória
- 🧩 notice_amount
  > `author_links.notice_amount` · `jsonb` · opcional
- 🕒 notice_title
  > `author_links.notice_title` · `timestamp` · opcional
- 🧩 donation_source
  > `author_links.donation_source` · `jsonb` · opcional
- 🧩 payment_position
  > `author_links.payment_position` · `jsonb` · obrigatória

### donation_links
<!-- orbit
id: "donation_links"
icon: "🧮"
collapsed: true
-->
> `donation_links` · tabela de Catálogo
>
> Uma linha por doação, com 17 colunas e cerca de 146 mil linhas.
>
> **Pertence a** [`report_logs_2`](#report_logs_2) · [`payment_versions`](#payment_versions) · [`book_settings`](#book_settings)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `donation_links.id` · `int8` · obrigatória
- ☑️ member_total
  > `donation_links.member_total` · `bool` · opcional
- ☑️ audit_archived
  > `donation_links.audit_archived` · `bool` · opcional
- 🕒 payment_archived
  > `donation_links.payment_archived` · `timestamp` · opcional
- 🔢 loan_source
  > `donation_links.loan_source` · `int4` · opcional
- ☑️ repair_region
  > `donation_links.repair_region` · `bool` · obrigatória
- 🔤 payment_created
  > `donation_links.payment_created` · `varchar` · obrigatória
- 🔢 role_notes
  > `donation_links.role_notes` · `int4` · obrigatória
- ☑️ member_ended
  > `donation_links.member_ended` · `bool` · opcional
- 🔗 notice_id <!-- orbit link: "#notice_counts", status: "chave estrangeira" -->
  > `donation_links.notice_id` · `int8` · obrigatória
  >
  > Aponta para [`notice_counts`](#notice_counts).
- ☑️ device_label
  > `donation_links.device_label` · `bool` · obrigatória
- 🕒 member_total_2
  > `donation_links.member_total_2` · `timestamp` · obrigatória
- 🕒 visit_started
  > `donation_links.visit_started` · `timestamp` · opcional
- 🔤 report_archived
  > `donation_links.report_archived` · `varchar` · obrigatória
- 🧩 staff_label
  > `donation_links.staff_label` · `jsonb` · opcional
- 🧩 donation_priority
  > `donation_links.donation_priority` · `jsonb` · opcional
- 🔗 tag_id <!-- orbit link: "#tag_requests", status: "chave estrangeira" -->
  > `donation_links.tag_id` · `int8` · obrigatória
  >
  > Aponta para [`tag_requests`](#tag_requests).

### budget_settings
<!-- orbit
id: "budget_settings"
icon: "📇"
collapsed: true
-->
> `budget_settings` · tabela de Catálogo
>
> Uma linha por orçamento, com 23 colunas e cerca de 153 mil linhas.
>
> **Pertence a** [`review_items`](#review_items) · [`visit_items`](#visit_items) · [`loan_logs_2`](#loan_logs_2) · [`card_queues`](#card_queues)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `budget_settings.id` · `int8` · obrigatória
- ☑️ card_language
  > `budget_settings.card_language` · `bool` · obrigatória
- ☑️ review_reviewed
  > `budget_settings.review_reviewed` · `bool` · opcional
- 🔤 fee_external
  > `budget_settings.fee_external` · `varchar` · opcional
- 🔤 event_reviewed
  > `budget_settings.event_reviewed` · `varchar` · obrigatória
- ☑️ donation_state
  > `budget_settings.donation_state` · `bool` · opcional
- 🔤 device_label
  > `budget_settings.device_label` · `varchar` · obrigatória
- 🔗 shelf_id <!-- orbit link: "#shelf_queues", status: "chave estrangeira" -->
  > `budget_settings.shelf_id` · `int8` · obrigatória
  >
  > Aponta para [`shelf_queues`](#shelf_queues).
- 🕒 vendor_started
  > `budget_settings.vendor_started` · `timestamp` · obrigatória
- 🕒 review_amount
  > `budget_settings.review_amount` · `timestamp` · obrigatória
- 🔢 vendor_active
  > `budget_settings.vendor_active` · `int4` · obrigatória
- ☑️ budget_started
  > `budget_settings.budget_started` · `bool` · opcional
- 🧩 role_format
  > `budget_settings.role_format` · `jsonb` · opcional
- 🔗 license_id <!-- orbit link: "#license_logs", status: "chave estrangeira" -->
  > `budget_settings.license_id` · `int8` · obrigatória
  >
  > Aponta para [`license_logs`](#license_logs).
- 🧩 notice_source
  > `budget_settings.notice_source` · `jsonb` · opcional
- 🔗 budget_id <!-- orbit link: "#budget_settings_2", status: "chave estrangeira" -->
  > `budget_settings.budget_id` · `int8` · obrigatória
  >
  > Aponta para [`budget_settings_2`](#budget_settings_2).
- 🧩 budget_code
  > `budget_settings.budget_code` · `jsonb` · obrigatória
- 🔢 device_started
  > `budget_settings.device_started` · `int4` · opcional
- 🔢 vendor_updated
  > `budget_settings.vendor_updated` · `int4` · opcional
- 🕒 device_started_2
  > `budget_settings.device_started_2` · `timestamp` · obrigatória
- 🔤 audit_started
  > `budget_settings.audit_started` · `varchar` · obrigatória
- 🧩 audit_source
  > `budget_settings.audit_source` · `jsonb` · obrigatória
- 🔗 shelf_id_2 <!-- orbit link: "#shelf_counts", status: "chave estrangeira" -->
  > `budget_settings.shelf_id_2` · `int8` · obrigatória
  >
  > Aponta para [`shelf_counts`](#shelf_counts).

### copy_items
<!-- orbit
id: "copy_items"
icon: "🧮"
collapsed: true
-->
> `copy_items` · tabela de Catálogo
>
> Uma linha por exemplar, com 11 colunas e cerca de 367 mil linhas.
>
> **Pertence a** [`review_queues`](#review_queues)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `copy_items.id` · `int8` · obrigatória
- 🧩 payment_reviewed
  > `copy_items.payment_reviewed` · `jsonb` · opcional
- 🔗 payment_id <!-- orbit link: "#payment_versions", status: "chave estrangeira" -->
  > `copy_items.payment_id` · `int8` · obrigatória
  >
  > Aponta para [`payment_versions`](#payment_versions).
- 🔤 copy_format
  > `copy_items.copy_format` · `varchar` · obrigatória
- 🕒 hold_updated
  > `copy_items.hold_updated` · `timestamp` · obrigatória
- 🧩 fee_kind
  > `copy_items.fee_kind` · `jsonb` · obrigatória
- ☑️ shelf_kind
  > `copy_items.shelf_kind` · `bool` · obrigatória
- 🔗 card_id <!-- orbit link: "#card_links_2", status: "chave estrangeira" -->
  > `copy_items.card_id` · `int8` · obrigatória
  >
  > Aponta para [`card_links_2`](#card_links_2).
- 🧩 event_language
  > `copy_items.event_language` · `jsonb` · opcional
- 🕒 shelf_updated
  > `copy_items.shelf_updated` · `timestamp` · obrigatória
- 🔗 order_id <!-- orbit link: "#order_items", status: "chave estrangeira" -->
  > `copy_items.order_id` · `int8` · obrigatória
  >
  > Aponta para [`order_items`](#order_items).

## 🧑‍🤝‍🧑 Leitores
<!-- orbit
id: "area-members"
color: "#2F7CF6"
-->
> 9 tabelas. As maiores: [`member_counts`](#member_counts), [`order_versions`](#order_versions), [`series_requests`](#series_requests).
>
> **Relacionada a** [`holds`](#holds) · [`audit_settings`](#audit_settings) · [`room_requests`](#room_requests)

### member_counts
<!-- orbit
id: "member_counts"
icon: "📇"
collapsed: true
-->
> `member_counts` · tabela de Leitores
>
> Uma linha por leitor, com 80 colunas e cerca de 175 mil linhas.
>
> **Pertence a** [`ebook_requests`](#ebook_requests)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `member_counts.id` · `int8` · obrigatória
- ☑️ series_active
  > `member_counts.series_active` · `bool` · opcional
- 🧩 branch_amount
  > `member_counts.branch_amount` · `jsonb` · obrigatória
- 🔤 visit_updated
  > `member_counts.visit_updated` · `varchar` · opcional
- ☑️ book_priority
  > `member_counts.book_priority` · `bool` · opcional
- 🔤 role_archived
  > `member_counts.role_archived` · `varchar` · obrigatória
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
- 🔗 payment_id <!-- orbit link: "#payment_settings", status: "chave estrangeira" -->
  > `member_counts.payment_id` · `int8` · obrigatória
  >
  > Aponta para [`payment_settings`](#payment_settings).
- ☑️ order_public
  > `member_counts.order_public` · `bool` · obrigatória
- 🔤 event_label
  > `member_counts.event_label` · `varchar` · opcional
- 🕒 ebook_reviewed
  > `member_counts.ebook_reviewed` · `timestamp` · obrigatória
- 🔢 book_label
  > `member_counts.book_label` · `int4` · obrigatória
- 🔗 budget_id <!-- orbit link: "#budget_queues", status: "chave estrangeira" -->
  > `member_counts.budget_id` · `int8` · obrigatória
  >
  > Aponta para [`budget_queues`](#budget_queues).
- ☑️ branch_code
  > `member_counts.branch_code` · `bool` · opcional
- ☑️ report_label
  > `member_counts.report_label` · `bool` · obrigatória
- 🔤 repair_state
  > `member_counts.repair_state` · `varchar` · obrigatória
- 🔗 member_id <!-- orbit link: "#member_counts_2", status: "chave estrangeira" -->
  > `member_counts.member_id` · `int8` · obrigatória
  >
  > Aponta para [`member_counts_2`](#member_counts_2).
- 🔢 tag_language
  > `member_counts.tag_language` · `int4` · opcional
- ☑️ staff_count
  > `member_counts.staff_count` · `bool` · opcional
- 🔤 fee_created
  > `member_counts.fee_created` · `varchar` · obrigatória
- 🕒 role_updated
  > `member_counts.role_updated` · `timestamp` · obrigatória
- 🔗 tag_id <!-- orbit link: "#tag_counts", status: "chave estrangeira" -->
  > `member_counts.tag_id` · `int8` · obrigatória
  >
  > Aponta para [`tag_counts`](#tag_counts).
- 🔢 loan_external
  > `member_counts.loan_external` · `int4` · opcional
- 🔤 copy_title
  > `member_counts.copy_title` · `varchar` · obrigatória
- 🔗 order_id <!-- orbit link: "#order_logs", status: "chave estrangeira" -->
  > `member_counts.order_id` · `int8` · obrigatória
  >
  > Aponta para [`order_logs`](#order_logs).
- 🕒 donation_code
  > `member_counts.donation_code` · `timestamp` · obrigatória
- 🔢 copy_archived
  > `member_counts.copy_archived` · `int4` · obrigatória
- 🔢 device_notes
  > `member_counts.device_notes` · `int4` · obrigatória
- 🔢 shelf_public
  > `member_counts.shelf_public` · `int4` · obrigatória
- 🕒 notice_amount
  > `member_counts.notice_amount` · `timestamp` · obrigatória
- 🔤 license_position
  > `member_counts.license_position` · `varchar` · obrigatória
- 🕒 ebook_notes
  > `member_counts.ebook_notes` · `timestamp` · opcional
- 🕒 branch_reviewed
  > `member_counts.branch_reviewed` · `timestamp` · obrigatória
- ☑️ budget_active
  > `member_counts.budget_active` · `bool` · obrigatória
- ☑️ series_active_2
  > `member_counts.series_active_2` · `bool` · opcional
- 🔢 notice_name
  > `member_counts.notice_name` · `int4` · obrigatória
- 🔢 hold_started
  > `member_counts.hold_started` · `int4` · obrigatória
- 🔗 report_id <!-- orbit link: "#report_counts", status: "chave estrangeira" -->
  > `member_counts.report_id` · `int8` · obrigatória
  >
  > Aponta para [`report_counts`](#report_counts).
- 🧩 hold_public
  > `member_counts.hold_public` · `jsonb` · opcional
- 🔗 card_id <!-- orbit link: "#card_queues", status: "chave estrangeira" -->
  > `member_counts.card_id` · `int8` · obrigatória
  >
  > Aponta para [`card_queues`](#card_queues).
- ☑️ series_format
  > `member_counts.series_format` · `bool` · opcional
- 🔗 staff_id <!-- orbit link: "#staff_logs", status: "chave estrangeira" -->
  > `member_counts.staff_id` · `int8` · obrigatória
  >
  > Aponta para [`staff_logs`](#staff_logs).
- 🔢 author_public
  > `member_counts.author_public` · `int4` · obrigatória
- 🔢 budget_status
  > `member_counts.budget_status` · `int4` · obrigatória
- ☑️ room_source
  > `member_counts.room_source` · `bool` · opcional
- 🔢 shelf_language
  > `member_counts.shelf_language` · `int4` · obrigatória
- 🔢 shelf_kind
  > `member_counts.shelf_kind` · `int4` · opcional
- 🔤 donation_format
  > `member_counts.donation_format` · `varchar` · opcional
- 🔢 shelf_updated
  > `member_counts.shelf_updated` · `int4` · obrigatória
- ☑️ audit_title
  > `member_counts.audit_title` · `bool` · opcional
- 🧩 member_ended
  > `member_counts.member_ended` · `jsonb` · obrigatória
- 🔗 vendor_id <!-- orbit link: "#vendor_history", status: "chave estrangeira" -->
  > `member_counts.vendor_id` · `int8` · obrigatória
  >
  > Aponta para [`vendor_history`](#vendor_history).
- 🕒 fee_reviewed
  > `member_counts.fee_reviewed` · `timestamp` · opcional
- 🔢 member_active
  > `member_counts.member_active` · `int4` · obrigatória
- 🧩 budget_source
  > `member_counts.budget_source` · `jsonb` · opcional
- ☑️ license_priority
  > `member_counts.license_priority` · `bool` · obrigatória
- 🔗 payment_id_2 <!-- orbit link: "#payment_requests", status: "chave estrangeira" -->
  > `member_counts.payment_id_2` · `int8` · obrigatória
  >
  > Aponta para [`payment_requests`](#payment_requests).
- 🕒 event_priority
  > `member_counts.event_priority` · `timestamp` · obrigatória
- 🔗 author_id <!-- orbit link: "#author_links", status: "chave estrangeira" -->
  > `member_counts.author_id` · `int8` · obrigatória
  >
  > Aponta para [`author_links`](#author_links).
- 🔗 copy_id <!-- orbit link: "#copy_counts", status: "chave estrangeira" -->
  > `member_counts.copy_id` · `int8` · obrigatória
  >
  > Aponta para [`copy_counts`](#copy_counts).
- 🔤 staff_code
  > `member_counts.staff_code` · `varchar` · opcional
- 🕒 card_code
  > `member_counts.card_code` · `timestamp` · opcional
- 🔢 role_notes
  > `member_counts.role_notes` · `int4` · opcional
- 🧩 member_public
  > `member_counts.member_public` · `jsonb` · opcional
- 🔗 loan_id <!-- orbit link: "#loan_history", status: "chave estrangeira" -->
  > `member_counts.loan_id` · `int8` · obrigatória
  >
  > Aponta para [`loan_history`](#loan_history).
- 🕒 payment_status
  > `member_counts.payment_status` · `timestamp` · obrigatória
- 🔤 branch_started
  > `member_counts.branch_started` · `varchar` · obrigatória
- ☑️ staff_state
  > `member_counts.staff_state` · `bool` · opcional
- ☑️ visit_label
  > `member_counts.visit_label` · `bool` · opcional
- 🧩 loan_created
  > `member_counts.loan_created` · `jsonb` · obrigatória
- 🧩 room_status
  > `member_counts.room_status` · `jsonb` · obrigatória
- 🕒 role_language
  > `member_counts.role_language` · `timestamp` · obrigatória
- 🔗 fee_id <!-- orbit link: "#fee_requests", status: "chave estrangeira" -->
  > `member_counts.fee_id` · `int8` · obrigatória
  >
  > Aponta para [`fee_requests`](#fee_requests).
- 🕒 visit_count
  > `member_counts.visit_count` · `timestamp` · opcional
- ☑️ loan_state
  > `member_counts.loan_state` · `bool` · obrigatória

### order_versions
<!-- orbit
id: "order_versions"
icon: "🧷"
collapsed: true
-->
> `order_versions` · tabela de Leitores
>
> Uma linha por pedido, com 18 colunas e cerca de 804 mil linhas.
>
> **Pertence a** [`device_items`](#device_items) · [`audit_queues`](#audit_queues) · [`report_items`](#report_items)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `order_versions.id` · `int8` · obrigatória
- 🕒 shelf_status
  > `order_versions.shelf_status` · `timestamp` · opcional
- 🔢 member_created
  > `order_versions.member_created` · `int4` · opcional
- 🔤 series_public
  > `order_versions.series_public` · `varchar` · obrigatória
- 🧩 member_kind
  > `order_versions.member_kind` · `jsonb` · obrigatória
- 🔢 copy_reviewed
  > `order_versions.copy_reviewed` · `int4` · obrigatória
- 🔗 series_id <!-- orbit link: "#series_counts", status: "chave estrangeira" -->
  > `order_versions.series_id` · `int8` · obrigatória
  >
  > Aponta para [`series_counts`](#series_counts).
- 🔢 role_started
  > `order_versions.role_started` · `int4` · opcional
- 🔤 copy_external
  > `order_versions.copy_external` · `varchar` · obrigatória
- 🔗 donation_id <!-- orbit link: "#donation_history", status: "chave estrangeira" -->
  > `order_versions.donation_id` · `int8` · obrigatória
  >
  > Aponta para [`donation_history`](#donation_history).
- ☑️ hold_created
  > `order_versions.hold_created` · `bool` · obrigatória
- 🧩 notice_created
  > `order_versions.notice_created` · `jsonb` · opcional
- 🔤 device_code
  > `order_versions.device_code` · `varchar` · obrigatória
- 🔢 license_updated
  > `order_versions.license_updated` · `int4` · obrigatória
- 🔗 loan_id <!-- orbit link: "#loan_logs", status: "chave estrangeira" -->
  > `order_versions.loan_id` · `int8` · obrigatória
  >
  > Aponta para [`loan_logs`](#loan_logs).
- 🔗 staff_id <!-- orbit link: "#staff_counts", status: "chave estrangeira" -->
  > `order_versions.staff_id` · `int8` · obrigatória
  >
  > Aponta para [`staff_counts`](#staff_counts).
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
> `series_requests` · tabela de Leitores
>
> Uma linha por série, com 6 colunas e cerca de 472 mil linhas.
>
> **Pertence a** [`device_settings`](#device_settings) · [`ebook_logs`](#ebook_logs)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `series_requests.id` · `int8` · obrigatória
- 🔢 book_started
  > `series_requests.book_started` · `int4` · obrigatória
- 🔢 report_updated
  > `series_requests.report_updated` · `int4` · opcional
- 🔤 ebook_count
  > `series_requests.ebook_count` · `varchar` · obrigatória
- 🔢 visit_code
  > `series_requests.visit_code` · `int4` · obrigatória
- 🔗 visit_id <!-- orbit link: "#visit_items", status: "chave estrangeira" -->
  > `series_requests.visit_id` · `int8` · obrigatória
  >
  > Aponta para [`visit_items`](#visit_items).

### donation_requests
<!-- orbit
id: "donation_requests"
icon: "🧷"
collapsed: true
-->
> `donation_requests` · tabela de Leitores
>
> Uma linha por doação, com 7 colunas e cerca de 764 mil linhas.
>
> **Pertence a** [`budgets`](#budgets) · [`review_items`](#review_items)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `donation_requests.id` · `int8` · obrigatória
- 🧩 hold_state
  > `donation_requests.hold_state` · `jsonb` · opcional
- 🔗 report_id <!-- orbit link: "#report_counts", status: "chave estrangeira" -->
  > `donation_requests.report_id` · `int8` · obrigatória
  >
  > Aponta para [`report_counts`](#report_counts).
- 🧩 loan_updated
  > `donation_requests.loan_updated` · `jsonb` · obrigatória
- 🕒 ebook_position
  > `donation_requests.ebook_position` · `timestamp` · opcional
- 🔢 device_started
  > `donation_requests.device_started` · `int4` · obrigatória
- ☑️ budget_amount
  > `donation_requests.budget_amount` · `bool` · obrigatória

### events
<!-- orbit
id: "events"
icon: "📇"
collapsed: true
-->
> `events` · tabela de Leitores
>
> Uma linha por evento, com 18 colunas e cerca de 464 mil linhas.
>
> **Pertence a** [`card_queues`](#card_queues) · [`report_versions`](#report_versions) · [`order_links`](#order_links)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `events.id` · `int8` · obrigatória
- 🔢 report_code
  > `events.report_code` · `int4` · obrigatória
- 🕒 ebook_updated
  > `events.ebook_updated` · `timestamp` · obrigatória
- 🕒 fee_external
  > `events.fee_external` · `timestamp` · opcional
- 🔢 device_notes
  > `events.device_notes` · `int4` · opcional
- 🧩 visit_total
  > `events.visit_total` · `jsonb` · obrigatória
- 🔗 book_id <!-- orbit link: "#book_links", status: "chave estrangeira" -->
  > `events.book_id` · `int8` · obrigatória
  >
  > Aponta para [`book_links`](#book_links).
- 🔢 hold_total
  > `events.hold_total` · `int4` · obrigatória
- 🔢 card_started
  > `events.card_started` · `int4` · opcional
- 🔤 order_position
  > `events.order_position` · `varchar` · obrigatória
- 🕒 shelf_archived
  > `events.shelf_archived` · `timestamp` · opcional
- 🔢 shelf_priority
  > `events.shelf_priority` · `int4` · obrigatória
- 🧩 loan_ended
  > `events.loan_ended` · `jsonb` · obrigatória
- 🔗 ebook_id <!-- orbit link: "#ebook_versions", status: "chave estrangeira" -->
  > `events.ebook_id` · `int8` · obrigatória
  >
  > Aponta para [`ebook_versions`](#ebook_versions).
- ☑️ fee_archived
  > `events.fee_archived` · `bool` · opcional
- 🔤 license_total
  > `events.license_total` · `varchar` · opcional
- 🧩 notice_active
  > `events.notice_active` · `jsonb` · opcional
- 🧩 author_total
  > `events.author_total` · `jsonb` · obrigatória

### report_queues
<!-- orbit
id: "report_queues"
icon: "🗂️"
collapsed: true
-->
> `report_queues` · tabela de Leitores
>
> Uma linha por relatório, com 17 colunas e cerca de 584 mil linhas.
>
> **Pertence a** [`holds_2`](#holds_2) · [`loan_counts`](#loan_counts)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `report_queues.id` · `int8` · obrigatória
- 🔢 repair_notes
  > `report_queues.repair_notes` · `int4` · obrigatória
- 🧩 notice_reviewed
  > `report_queues.notice_reviewed` · `jsonb` · opcional
- 🔗 holds_id <!-- orbit link: "#holds", status: "chave estrangeira" -->
  > `report_queues.holds_id` · `int8` · obrigatória
  >
  > Aponta para [`holds`](#holds).
- 🧩 report_language
  > `report_queues.report_language` · `jsonb` · obrigatória
- 🔗 author_id <!-- orbit link: "#author_links_3", status: "chave estrangeira" -->
  > `report_queues.author_id` · `int8` · obrigatória
  >
  > Aponta para [`author_links_3`](#author_links_3).
- 🧩 notice_code
  > `report_queues.notice_code` · `jsonb` · obrigatória
- 🕒 report_status
  > `report_queues.report_status` · `timestamp` · opcional
- 🔢 member_status
  > `report_queues.member_status` · `int4` · obrigatória
- 🔢 tag_name
  > `report_queues.tag_name` · `int4` · opcional
- 🔢 copy_public
  > `report_queues.copy_public` · `int4` · opcional
- 🔢 copy_reviewed
  > `report_queues.copy_reviewed` · `int4` · opcional
- ☑️ audit_name
  > `report_queues.audit_name` · `bool` · opcional
- ☑️ hold_label
  > `report_queues.hold_label` · `bool` · obrigatória
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
> `card_links` · tabela de Leitores
>
> Uma linha por carteirinha, com 17 colunas e cerca de 262 mil linhas.
>
> **Pertence a** [`notice_requests`](#notice_requests) · [`loan_logs`](#loan_logs)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `card_links.id` · `int8` · obrigatória
- 🕒 branch_name
  > `card_links.branch_name` · `timestamp` · obrigatória
- 🔤 ebook_label
  > `card_links.ebook_label` · `varchar` · obrigatória
- 🔗 license_id <!-- orbit link: "#license_queues", status: "chave estrangeira" -->
  > `card_links.license_id` · `int8` · obrigatória
  >
  > Aponta para [`license_queues`](#license_queues).
- ☑️ shelf_created
  > `card_links.shelf_created` · `bool` · opcional
- 🧩 card_status
  > `card_links.card_status` · `jsonb` · obrigatória
- 🔗 budget_id <!-- orbit link: "#budget_settings", status: "chave estrangeira" -->
  > `card_links.budget_id` · `int8` · obrigatória
  >
  > Aponta para [`budget_settings`](#budget_settings).
- ☑️ review_position
  > `card_links.review_position` · `bool` · obrigatória
- 🧩 donation_source
  > `card_links.donation_source` · `jsonb` · opcional
- 🔤 series_ended
  > `card_links.series_ended` · `varchar` · opcional
- 🔗 donation_id <!-- orbit link: "#donation_history", status: "chave estrangeira" -->
  > `card_links.donation_id` · `int8` · obrigatória
  >
  > Aponta para [`donation_history`](#donation_history).
- 🔢 hold_count
  > `card_links.hold_count` · `int4` · obrigatória
- 🔤 loan_total
  > `card_links.loan_total` · `varchar` · obrigatória
- 🔗 order_id <!-- orbit link: "#order_items", status: "chave estrangeira" -->
  > `card_links.order_id` · `int8` · obrigatória
  >
  > Aponta para [`order_items`](#order_items).
- 🔤 room_ended
  > `card_links.room_ended` · `varchar` · opcional
- 🕒 device_created
  > `card_links.device_created` · `timestamp` · obrigatória
- 🔢 order_count
  > `card_links.order_count` · `int4` · obrigatória

### budget_queues
<!-- orbit
id: "budget_queues"
icon: "📇"
collapsed: true
-->
> `budget_queues` · tabela de Leitores
>
> Uma linha por orçamento, com 16 colunas e cerca de 189 mil linhas.
>
> **Pertence a** [`license_queues_2`](#license_queues_2) · [`loan_logs_2`](#loan_logs_2)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `budget_queues.id` · `int8` · obrigatória
- 🔗 copy_id <!-- orbit link: "#copy_links", status: "chave estrangeira" -->
  > `budget_queues.copy_id` · `int8` · obrigatória
  >
  > Aponta para [`copy_links`](#copy_links).
- ☑️ member_archived
  > `budget_queues.member_archived` · `bool` · opcional
- 🧩 room_priority
  > `budget_queues.room_priority` · `jsonb` · obrigatória
- 🧩 tag_priority
  > `budget_queues.tag_priority` · `jsonb` · obrigatória
- 🔗 payment_id <!-- orbit link: "#payment_settings", status: "chave estrangeira" -->
  > `budget_queues.payment_id` · `int8` · obrigatória
  >
  > Aponta para [`payment_settings`](#payment_settings).
- 🔢 repair_notes
  > `budget_queues.repair_notes` · `int4` · obrigatória
- 🧩 book_archived
  > `budget_queues.book_archived` · `jsonb` · opcional
- 🔗 review_id <!-- orbit link: "#review_counts", status: "chave estrangeira" -->
  > `budget_queues.review_id` · `int8` · obrigatória
  >
  > Aponta para [`review_counts`](#review_counts).
- 🔢 budget_language
  > `budget_queues.budget_language` · `int4` · obrigatória
- 🧩 payment_reviewed
  > `budget_queues.payment_reviewed` · `jsonb` · opcional
- ☑️ vendor_created
  > `budget_queues.vendor_created` · `bool` · opcional
- 🔢 event_active
  > `budget_queues.event_active` · `int4` · obrigatória
- ☑️ book_name
  > `budget_queues.book_name` · `bool` · obrigatória
- 🕒 fee_kind
  > `budget_queues.fee_kind` · `timestamp` · obrigatória
- 🔗 tag_id <!-- orbit link: "#tag_settings_2", status: "chave estrangeira" -->
  > `budget_queues.tag_id` · `int8` · obrigatória
  >
  > Aponta para [`tag_settings_2`](#tag_settings_2).

### audit_links
<!-- orbit
id: "audit_links"
icon: "📗"
collapsed: true
-->
> `audit_links` · tabela de Leitores
>
> Uma linha por auditoria, com 21 colunas e cerca de 25 mil linhas.
>
> **Pertence a** [`budget_settings`](#budget_settings) · [`donation_history`](#donation_history) · [`book_items`](#book_items) · [`donation_queues`](#donation_queues)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `audit_links.id` · `int8` · obrigatória
- 🕒 loan_updated
  > `audit_links.loan_updated` · `timestamp` · opcional
- 🧩 notice_updated
  > `audit_links.notice_updated` · `jsonb` · obrigatória
- 🕒 device_kind
  > `audit_links.device_kind` · `timestamp` · opcional
- 🧩 series_ended
  > `audit_links.series_ended` · `jsonb` · opcional
- ☑️ card_position
  > `audit_links.card_position` · `bool` · obrigatória
- 🔗 shelf_id <!-- orbit link: "#shelf_queues", status: "chave estrangeira" -->
  > `audit_links.shelf_id` · `int8` · obrigatória
  >
  > Aponta para [`shelf_queues`](#shelf_queues).
- 🔢 payment_amount
  > `audit_links.payment_amount` · `int4` · obrigatória
- ☑️ book_status
  > `audit_links.book_status` · `bool` · obrigatória
- 🔤 donation_format
  > `audit_links.donation_format` · `varchar` · obrigatória
- 🔤 donation_state
  > `audit_links.donation_state` · `varchar` · obrigatória
- 🔤 shelf_created
  > `audit_links.shelf_created` · `varchar` · opcional
- 🔤 tag_region
  > `audit_links.tag_region` · `varchar` · obrigatória
- 🧩 copy_label
  > `audit_links.copy_label` · `jsonb` · opcional
- 🔤 order_name
  > `audit_links.order_name` · `varchar` · obrigatória
- ☑️ shelf_reviewed
  > `audit_links.shelf_reviewed` · `bool` · obrigatória
- 🔢 payment_source
  > `audit_links.payment_source` · `int4` · opcional
- 🧩 device_label
  > `audit_links.device_label` · `jsonb` · opcional
- 🔤 notice_position
  > `audit_links.notice_position` · `varchar` · obrigatória
- 🔢 device_count
  > `audit_links.device_count` · `int4` · opcional
- 🔗 review_id <!-- orbit link: "#review_versions", status: "chave estrangeira" -->
  > `audit_links.review_id` · `int8` · obrigatória
  >
  > Aponta para [`review_versions`](#review_versions).

## 🔁 Empréstimos e devoluções
<!-- orbit
id: "area-loans-returns"
color: "#2E9E5B"
-->
> 9 tabelas. As maiores: [`series_counts`](#series_counts), [`audit_links_2`](#audit_links_2), [`notice_counts`](#notice_counts).
>
> **Relacionada a** [`branchs`](#branchs) · [`budget_queues`](#budget_queues) · [`license_settings`](#license_settings)

### series_counts
<!-- orbit
id: "series_counts"
icon: "📇"
collapsed: true
-->
> `series_counts` · tabela de Empréstimos e devoluções
>
> Uma linha por série, com 15 colunas e cerca de 468 mil linhas.
>
> **Pertence a** [`holds`](#holds)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `series_counts.id` · `int8` · obrigatória
- 🕒 loan_format
  > `series_counts.loan_format` · `timestamp` · opcional
- 🔗 budget_id <!-- orbit link: "#budget_settings_2", status: "chave estrangeira" -->
  > `series_counts.budget_id` · `int8` · obrigatória
  >
  > Aponta para [`budget_settings_2`](#budget_settings_2).
- 🔗 member_id <!-- orbit link: "#member_counts_2", status: "chave estrangeira" -->
  > `series_counts.member_id` · `int8` · obrigatória
  >
  > Aponta para [`member_counts_2`](#member_counts_2).
- 🔗 payment_id <!-- orbit link: "#payment_versions", status: "chave estrangeira" -->
  > `series_counts.payment_id` · `int8` · obrigatória
  >
  > Aponta para [`payment_versions`](#payment_versions).
- 🔗 license_id <!-- orbit link: "#license_queues", status: "chave estrangeira" -->
  > `series_counts.license_id` · `int8` · obrigatória
  >
  > Aponta para [`license_queues`](#license_queues).
- 🔢 copy_priority
  > `series_counts.copy_priority` · `int4` · obrigatória
- 🕒 card_title
  > `series_counts.card_title` · `timestamp` · obrigatória
- 🧩 role_title
  > `series_counts.role_title` · `jsonb` · obrigatória
- 🕒 book_priority
  > `series_counts.book_priority` · `timestamp` · obrigatória
- 🔢 fee_label
  > `series_counts.fee_label` · `int4` · opcional
- 🕒 staff_format
  > `series_counts.staff_format` · `timestamp` · obrigatória
- 🕒 notice_state
  > `series_counts.notice_state` · `timestamp` · obrigatória
- 🔗 donation_id <!-- orbit link: "#donation_links", status: "chave estrangeira" -->
  > `series_counts.donation_id` · `int8` · obrigatória
  >
  > Aponta para [`donation_links`](#donation_links).
- 🔤 copy_created
  > `series_counts.copy_created` · `varchar` · opcional

### audit_links_2
<!-- orbit
id: "audit_links_2"
icon: "📗"
collapsed: true
-->
> `audit_links_2` · tabela de Empréstimos e devoluções
>
> Uma linha por auditoria, com 21 colunas e cerca de 2 mil linhas.
>
> **Pertence a** [`report_queues`](#report_queues) · [`license_settings`](#license_settings) · [`tag_counts`](#tag_counts)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `audit_links_2.id` · `int8` · obrigatória
- 🔤 license_ended
  > `audit_links_2.license_ended` · `varchar` · opcional
- 🔤 member_count
  > `audit_links_2.member_count` · `varchar` · obrigatória
- ☑️ event_format
  > `audit_links_2.event_format` · `bool` · obrigatória
- 🔤 member_created
  > `audit_links_2.member_created` · `varchar` · obrigatória
- 🔗 fee_id <!-- orbit link: "#fee_requests", status: "chave estrangeira" -->
  > `audit_links_2.fee_id` · `int8` · obrigatória
  >
  > Aponta para [`fee_requests`](#fee_requests).
- 🕒 fee_ended
  > `audit_links_2.fee_ended` · `timestamp` · obrigatória
- 🔗 card_id <!-- orbit link: "#card_logs", status: "chave estrangeira" -->
  > `audit_links_2.card_id` · `int8` · obrigatória
  >
  > Aponta para [`card_logs`](#card_logs).
- 🔢 visit_source
  > `audit_links_2.visit_source` · `int4` · opcional
- 🔢 repair_region
  > `audit_links_2.repair_region` · `int4` · opcional
- ☑️ card_language
  > `audit_links_2.card_language` · `bool` · obrigatória
- 🕒 series_created
  > `audit_links_2.series_created` · `timestamp` · opcional
- 🔤 donation_region
  > `audit_links_2.donation_region` · `varchar` · opcional
- ☑️ staff_name
  > `audit_links_2.staff_name` · `bool` · opcional
- 🧩 report_kind
  > `audit_links_2.report_kind` · `jsonb` · opcional
- ☑️ loan_archived
  > `audit_links_2.loan_archived` · `bool` · obrigatória
- 🔗 budget_id <!-- orbit link: "#budget_settings", status: "chave estrangeira" -->
  > `audit_links_2.budget_id` · `int8` · obrigatória
  >
  > Aponta para [`budget_settings`](#budget_settings).
- ☑️ branch_region
  > `audit_links_2.branch_region` · `bool` · obrigatória
- 🔗 visit_id <!-- orbit link: "#visit_queues", status: "chave estrangeira" -->
  > `audit_links_2.visit_id` · `int8` · obrigatória
  >
  > Aponta para [`visit_queues`](#visit_queues).
- 🕒 card_language_2
  > `audit_links_2.card_language_2` · `timestamp` · opcional
- 🔢 author_source
  > `audit_links_2.author_source` · `int4` · obrigatória

### notice_counts
<!-- orbit
id: "notice_counts"
icon: "🗂️"
collapsed: true
-->
> `notice_counts` · tabela de Empréstimos e devoluções
>
> Uma linha por aviso, com 15 colunas e cerca de 426 mil linhas.
>
> **Pertence a** [`ebook_logs`](#ebook_logs) · [`device_items`](#device_items) · [`book_links`](#book_links)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `notice_counts.id` · `int8` · obrigatória
- 🔢 report_archived
  > `notice_counts.report_archived` · `int4` · obrigatória
- 🔤 role_status
  > `notice_counts.role_status` · `varchar` · obrigatória
- ☑️ visit_active
  > `notice_counts.visit_active` · `bool` · obrigatória
- 🧩 visit_external
  > `notice_counts.visit_external` · `jsonb` · obrigatória
- 🧩 shelf_started
  > `notice_counts.shelf_started` · `jsonb` · obrigatória
- 🕒 author_amount
  > `notice_counts.author_amount` · `timestamp` · opcional
- 🧩 tag_source
  > `notice_counts.tag_source` · `jsonb` · obrigatória
- 🕒 review_name
  > `notice_counts.review_name` · `timestamp` · opcional
- 🔗 hold_id <!-- orbit link: "#hold_links", status: "chave estrangeira" -->
  > `notice_counts.hold_id` · `int8` · obrigatória
  >
  > Aponta para [`hold_links`](#hold_links).
- ☑️ member_label
  > `notice_counts.member_label` · `bool` · opcional
- 🔗 rooms_id <!-- orbit link: "#rooms", status: "chave estrangeira" -->
  > `notice_counts.rooms_id` · `int8` · obrigatória
  >
  > Aponta para [`rooms`](#rooms).
- 🕒 event_state
  > `notice_counts.event_state` · `timestamp` · obrigatória
- ☑️ payment_label
  > `notice_counts.payment_label` · `bool` · obrigatória
- 🧩 visit_reviewed
  > `notice_counts.visit_reviewed` · `jsonb` · obrigatória

### shelfs
<!-- orbit
id: "shelfs"
icon: "📇"
collapsed: true
-->
> `shelfs` · tabela de Empréstimos e devoluções
>
> Uma linha por estante, com 4 colunas e cerca de 423 mil linhas.
>
> **Pertence a** [`tag_counts`](#tag_counts)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `shelfs.id` · `int8` · obrigatória
- 🕒 role_started
  > `shelfs.role_started` · `timestamp` · opcional
- 🔤 donation_public
  > `shelfs.donation_public` · `varchar` · opcional
- 🕒 audit_count
  > `shelfs.audit_count` · `timestamp` · obrigatória

### tag_settings
<!-- orbit
id: "tag_settings"
icon: "📇"
collapsed: true
-->
> `tag_settings` · tabela de Empréstimos e devoluções
>
> Uma linha por etiqueta, com 4 colunas e cerca de 784 mil linhas.
>
> **Pertence a** [`author_links_2`](#author_links_2) · [`fee_requests`](#fee_requests) · [`loan_logs_2`](#loan_logs_2) · [`card_queues`](#card_queues)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `tag_settings.id` · `int8` · obrigatória
- 🧩 tag_status
  > `tag_settings.tag_status` · `jsonb` · opcional
- 🔢 fee_state
  > `tag_settings.fee_state` · `int4` · obrigatória
- 🔢 notice_reviewed
  > `tag_settings.notice_reviewed` · `int4` · obrigatória

### staff_counts
<!-- orbit
id: "staff_counts"
icon: "📗"
collapsed: true
-->
> `staff_counts` · tabela de Empréstimos e devoluções
>
> Uma linha por funcionário, com 24 colunas e cerca de 697 mil linhas.
>
> **Pertence a** [`copy_links`](#copy_links) · [`ebook_queues`](#ebook_queues)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `staff_counts.id` · `int8` · obrigatória
- 🧩 role_reviewed
  > `staff_counts.role_reviewed` · `jsonb` · opcional
- 🔤 shelf_updated
  > `staff_counts.shelf_updated` · `varchar` · opcional
- 🧩 member_created
  > `staff_counts.member_created` · `jsonb` · opcional
- 🕒 role_language
  > `staff_counts.role_language` · `timestamp` · opcional
- 🔢 hold_updated
  > `staff_counts.hold_updated` · `int4` · obrigatória
- 🔤 report_total
  > `staff_counts.report_total` · `varchar` · obrigatória
- 🔢 branch_public
  > `staff_counts.branch_public` · `int4` · obrigatória
- ☑️ tag_created
  > `staff_counts.tag_created` · `bool` · obrigatória
- 🔗 holds_id <!-- orbit link: "#holds", status: "chave estrangeira" -->
  > `staff_counts.holds_id` · `int8` · obrigatória
  >
  > Aponta para [`holds`](#holds).
- ☑️ loan_created
  > `staff_counts.loan_created` · `bool` · obrigatória
- 🔢 review_region
  > `staff_counts.review_region` · `int4` · obrigatória
- 🔤 series_language
  > `staff_counts.series_language` · `varchar` · obrigatória
- 🔢 donation_state
  > `staff_counts.donation_state` · `int4` · obrigatória
- 🔤 series_ended
  > `staff_counts.series_ended` · `varchar` · opcional
- 🔗 loan_id <!-- orbit link: "#loan_counts", status: "chave estrangeira" -->
  > `staff_counts.loan_id` · `int8` · obrigatória
  >
  > Aponta para [`loan_counts`](#loan_counts).
- ☑️ audit_started
  > `staff_counts.audit_started` · `bool` · opcional
- 🧩 donation_amount
  > `staff_counts.donation_amount` · `jsonb` · obrigatória
- ☑️ vendor_active
  > `staff_counts.vendor_active` · `bool` · obrigatória
- 🧩 order_reviewed
  > `staff_counts.order_reviewed` · `jsonb` · obrigatória
- ☑️ author_position
  > `staff_counts.author_position` · `bool` · opcional
- 🕒 review_count
  > `staff_counts.review_count` · `timestamp` · obrigatória
- 🔢 room_count
  > `staff_counts.room_count` · `int4` · obrigatória
- 🔗 license_id <!-- orbit link: "#license_queues", status: "chave estrangeira" -->
  > `staff_counts.license_id` · `int8` · obrigatória
  >
  > Aponta para [`license_queues`](#license_queues).

### audit_settings
<!-- orbit
id: "audit_settings"
icon: "📗"
collapsed: true
-->
> `audit_settings` · tabela de Empréstimos e devoluções
>
> Uma linha por auditoria, com 20 colunas e cerca de 644 mil linhas.
>
> **Pertence a** [`staff_settings`](#staff_settings)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `audit_settings.id` · `int8` · obrigatória
- 🕒 device_priority
  > `audit_settings.device_priority` · `timestamp` · opcional
- 🔗 tag_id <!-- orbit link: "#tag_settings", status: "chave estrangeira" -->
  > `audit_settings.tag_id` · `int8` · obrigatória
  >
  > Aponta para [`tag_settings`](#tag_settings).
- 🧩 tag_kind
  > `audit_settings.tag_kind` · `jsonb` · opcional
- 🔗 member_id <!-- orbit link: "#member_counts", status: "chave estrangeira" -->
  > `audit_settings.member_id` · `int8` · obrigatória
  >
  > Aponta para [`member_counts`](#member_counts).
- 🧩 repair_language
  > `audit_settings.repair_language` · `jsonb` · opcional
- 🕒 ebook_priority
  > `audit_settings.ebook_priority` · `timestamp` · obrigatória
- 🔤 ebook_updated
  > `audit_settings.ebook_updated` · `varchar` · opcional
- 🧩 repair_total
  > `audit_settings.repair_total` · `jsonb` · opcional
- ☑️ member_total
  > `audit_settings.member_total` · `bool` · opcional
- 🔗 order_id <!-- orbit link: "#order_versions", status: "chave estrangeira" -->
  > `audit_settings.order_id` · `int8` · obrigatória
  >
  > Aponta para [`order_versions`](#order_versions).
- 🧩 audit_format
  > `audit_settings.audit_format` · `jsonb` · opcional
- 🕒 review_region
  > `audit_settings.review_region` · `timestamp` · opcional
- 🔗 budget_id <!-- orbit link: "#budget_queues", status: "chave estrangeira" -->
  > `audit_settings.budget_id` · `int8` · obrigatória
  >
  > Aponta para [`budget_queues`](#budget_queues).
- 🔢 room_archived
  > `audit_settings.room_archived` · `int4` · obrigatória
- ☑️ member_label
  > `audit_settings.member_label` · `bool` · opcional
- 🔗 event_id <!-- orbit link: "#event_queues", status: "chave estrangeira" -->
  > `audit_settings.event_id` · `int8` · obrigatória
  >
  > Aponta para [`event_queues`](#event_queues).
- 🔤 notice_region
  > `audit_settings.notice_region` · `varchar` · obrigatória
- 🔗 license_id <!-- orbit link: "#license_settings", status: "chave estrangeira" -->
  > `audit_settings.license_id` · `int8` · obrigatória
  >
  > Aponta para [`license_settings`](#license_settings).
- 🔗 card_id <!-- orbit link: "#card_links", status: "chave estrangeira" -->
  > `audit_settings.card_id` · `int8` · obrigatória
  >
  > Aponta para [`card_links`](#card_links).

### review_items
<!-- orbit
id: "review_items"
icon: "🧷"
collapsed: true
-->
> `review_items` · tabela de Empréstimos e devoluções
>
> Uma linha por resenha, com 24 colunas e cerca de 74 mil linhas.
>
> **Pertence a** [`donations`](#donations)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `review_items.id` · `int8` · obrigatória
- 🔗 licenses_id <!-- orbit link: "#licenses", status: "chave estrangeira" -->
  > `review_items.licenses_id` · `int8` · obrigatória
  >
  > Aponta para [`licenses`](#licenses).
- 🔢 copy_external
  > `review_items.copy_external` · `int4` · opcional
- 🧩 event_label
  > `review_items.event_label` · `jsonb` · obrigatória
- 🔢 role_language
  > `review_items.role_language` · `int4` · opcional
- ☑️ budget_reviewed
  > `review_items.budget_reviewed` · `bool` · obrigatória
- 🔗 loan_id <!-- orbit link: "#loan_history", status: "chave estrangeira" -->
  > `review_items.loan_id` · `int8` · obrigatória
  >
  > Aponta para [`loan_history`](#loan_history).
- 🔤 ebook_name
  > `review_items.ebook_name` · `varchar` · obrigatória
- 🔗 visit_id <!-- orbit link: "#visit_links", status: "chave estrangeira" -->
  > `review_items.visit_id` · `int8` · obrigatória
  >
  > Aponta para [`visit_links`](#visit_links).
- 🔢 payment_active
  > `review_items.payment_active` · `int4` · opcional
- ☑️ shelf_source
  > `review_items.shelf_source` · `bool` · obrigatória
- 🧩 shelf_reviewed
  > `review_items.shelf_reviewed` · `jsonb` · opcional
- 🔢 series_name
  > `review_items.series_name` · `int4` · opcional
- ☑️ donation_source
  > `review_items.donation_source` · `bool` · obrigatória
- 🔤 copy_reviewed
  > `review_items.copy_reviewed` · `varchar` · obrigatória
- 🔗 audit_id <!-- orbit link: "#audit_links", status: "chave estrangeira" -->
  > `review_items.audit_id` · `int8` · obrigatória
  >
  > Aponta para [`audit_links`](#audit_links).
- 🕒 copy_state
  > `review_items.copy_state` · `timestamp` · opcional
- 🔗 member_id <!-- orbit link: "#member_history", status: "chave estrangeira" -->
  > `review_items.member_id` · `int8` · obrigatória
  >
  > Aponta para [`member_history`](#member_history).
- ☑️ license_total
  > `review_items.license_total` · `bool` · obrigatória
- 🔗 role_id <!-- orbit link: "#role_counts", status: "chave estrangeira" -->
  > `review_items.role_id` · `int8` · obrigatória
  >
  > Aponta para [`role_counts`](#role_counts).
- 🔢 branch_created
  > `review_items.branch_created` · `int4` · opcional
- ☑️ card_created
  > `review_items.card_created` · `bool` · opcional
- ☑️ room_total
  > `review_items.room_total` · `bool` · opcional
- 🔤 report_started
  > `review_items.report_started` · `varchar` · obrigatória

### staff_items
<!-- orbit
id: "staff_items"
icon: "📗"
collapsed: true
-->
> `staff_items` · tabela de Empréstimos e devoluções
>
> Uma linha por funcionário, com 12 colunas e cerca de 118 mil linhas.
>
> **Pertence a** [`audit_links_2`](#audit_links_2) · [`copy_versions`](#copy_versions) · [`budgets`](#budgets)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `staff_items.id` · `int8` · obrigatória
- 🔢 device_source
  > `staff_items.device_source` · `int4` · obrigatória
- 🔢 staff_state
  > `staff_items.staff_state` · `int4` · obrigatória
- 🧩 review_notes
  > `staff_items.review_notes` · `jsonb` · opcional
- 🧩 vendor_total
  > `staff_items.vendor_total` · `jsonb` · obrigatória
- 🔢 tag_region
  > `staff_items.tag_region` · `int4` · obrigatória
- 🔢 branch_total
  > `staff_items.branch_total` · `int4` · opcional
- 🔤 ebook_amount
  > `staff_items.ebook_amount` · `varchar` · obrigatória
- 🕒 payment_position
  > `staff_items.payment_position` · `timestamp` · opcional
- 🔢 role_position
  > `staff_items.role_position` · `int4` · opcional
- 🔤 copy_state
  > `staff_items.copy_state` · `varchar` · opcional
- ☑️ ebook_code
  > `staff_items.ebook_code` · `bool` · opcional

## 🏛️ Unidades
<!-- orbit
id: "area-branches"
color: "#8E4EC6"
-->
> 10 tabelas. As maiores: [`tag_counts`](#tag_counts), [`role_counts`](#role_counts), [`branch_links`](#branch_links).
>
> **Relacionada a** [`loan_logs`](#loan_logs) · [`copy_versions`](#copy_versions) · [`vendor_items`](#vendor_items)

### tag_counts
<!-- orbit
id: "tag_counts"
icon: "🧷"
collapsed: true
-->
> `tag_counts` · tabela de Unidades
>
> Uma linha por etiqueta, com 17 colunas e cerca de 389 mil linhas.
>
> **Pertence a** [`review_versions`](#review_versions) · [`member_counts_2`](#member_counts_2) · [`hold_history`](#hold_history)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `tag_counts.id` · `int8` · obrigatória
- 🧩 member_updated
  > `tag_counts.member_updated` · `jsonb` · opcional
- 🔗 review_id <!-- orbit link: "#review_items", status: "chave estrangeira" -->
  > `tag_counts.review_id` · `int8` · obrigatória
  >
  > Aponta para [`review_items`](#review_items).
- 🔗 licenses_id <!-- orbit link: "#licenses", status: "chave estrangeira" -->
  > `tag_counts.licenses_id` · `int8` · obrigatória
  >
  > Aponta para [`licenses`](#licenses).
- 🕒 room_code
  > `tag_counts.room_code` · `timestamp` · opcional
- 🔢 shelf_status
  > `tag_counts.shelf_status` · `int4` · opcional
- 🔤 payment_count
  > `tag_counts.payment_count` · `varchar` · opcional
- 🔢 book_reviewed
  > `tag_counts.book_reviewed` · `int4` · opcional
- 🧩 room_public
  > `tag_counts.room_public` · `jsonb` · obrigatória
- 🔤 budget_archived
  > `tag_counts.budget_archived` · `varchar` · opcional
- 🔗 order_id <!-- orbit link: "#order_logs", status: "chave estrangeira" -->
  > `tag_counts.order_id` · `int8` · obrigatória
  >
  > Aponta para [`order_logs`](#order_logs).
- 🔗 ebook_id <!-- orbit link: "#ebook_queues", status: "chave estrangeira" -->
  > `tag_counts.ebook_id` · `int8` · obrigatória
  >
  > Aponta para [`ebook_queues`](#ebook_queues).
- 🔗 report_id <!-- orbit link: "#report_queues_2", status: "chave estrangeira" -->
  > `tag_counts.report_id` · `int8` · obrigatória
  >
  > Aponta para [`report_queues_2`](#report_queues_2).
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
> `role_counts` · tabela de Unidades
>
> Uma linha por função, com 24 colunas e cerca de 767 mil linhas.
>
> **Pertence a** [`shelfs`](#shelfs) · [`visit_items`](#visit_items)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `role_counts.id` · `int8` · obrigatória
- ☑️ review_language
  > `role_counts.review_language` · `bool` · opcional
- ☑️ copy_name
  > `role_counts.copy_name` · `bool` · opcional
- 🔗 loan_id <!-- orbit link: "#loan_logs", status: "chave estrangeira" -->
  > `role_counts.loan_id` · `int8` · obrigatória
  >
  > Aponta para [`loan_logs`](#loan_logs).
- ☑️ member_name
  > `role_counts.member_name` · `bool` · obrigatória
- 🧩 order_reviewed
  > `role_counts.order_reviewed` · `jsonb` · opcional
- 🔤 shelf_active
  > `role_counts.shelf_active` · `varchar` · opcional
- 🔢 donation_position
  > `role_counts.donation_position` · `int4` · obrigatória
- 🔢 loan_priority
  > `role_counts.loan_priority` · `int4` · opcional
- 🔗 notice_id <!-- orbit link: "#notice_counts", status: "chave estrangeira" -->
  > `role_counts.notice_id` · `int8` · obrigatória
  >
  > Aponta para [`notice_counts`](#notice_counts).
- ☑️ notice_ended
  > `role_counts.notice_ended` · `bool` · obrigatória
- 🔢 tag_total
  > `role_counts.tag_total` · `int4` · opcional
- ☑️ vendor_priority
  > `role_counts.vendor_priority` · `bool` · obrigatória
- 🔗 hold_id <!-- orbit link: "#hold_logs", status: "chave estrangeira" -->
  > `role_counts.hold_id` · `int8` · obrigatória
  >
  > Aponta para [`hold_logs`](#hold_logs).
- 🔢 vendor_reviewed
  > `role_counts.vendor_reviewed` · `int4` · obrigatória
- 🔢 order_updated
  > `role_counts.order_updated` · `int4` · opcional
- 🔤 room_started
  > `role_counts.room_started` · `varchar` · obrigatória
- 🕒 license_source
  > `role_counts.license_source` · `timestamp` · opcional
- 🕒 loan_format
  > `role_counts.loan_format` · `timestamp` · opcional
- 🔢 series_notes
  > `role_counts.series_notes` · `int4` · opcional
- 🕒 report_public
  > `role_counts.report_public` · `timestamp` · obrigatória
- 🔗 fee_id <!-- orbit link: "#fee_requests", status: "chave estrangeira" -->
  > `role_counts.fee_id` · `int8` · obrigatória
  >
  > Aponta para [`fee_requests`](#fee_requests).
- ☑️ notice_created
  > `role_counts.notice_created` · `bool` · obrigatória
- 🕒 order_status
  > `role_counts.order_status` · `timestamp` · opcional

### branch_links
<!-- orbit
id: "branch_links"
icon: "📗"
collapsed: true
-->
> `branch_links` · tabela de Unidades
>
> Uma linha por unidade, com 20 colunas e cerca de 423 mil linhas.
>
> **Pertence a** [`room_counts`](#room_counts)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `branch_links.id` · `int8` · obrigatória
- 🔗 author_id <!-- orbit link: "#author_history", status: "chave estrangeira" -->
  > `branch_links.author_id` · `int8` · obrigatória
  >
  > Aponta para [`author_history`](#author_history).
- 🔗 report_id <!-- orbit link: "#report_items", status: "chave estrangeira" -->
  > `branch_links.report_id` · `int8` · obrigatória
  >
  > Aponta para [`report_items`](#report_items).
- ☑️ staff_kind
  > `branch_links.staff_kind` · `bool` · opcional
- 🕒 hold_language
  > `branch_links.hold_language` · `timestamp` · opcional
- 🔢 loan_archived
  > `branch_links.loan_archived` · `int4` · obrigatória
- ☑️ event_started
  > `branch_links.event_started` · `bool` · opcional
- 🔢 author_updated
  > `branch_links.author_updated` · `int4` · opcional
- 🔤 staff_reviewed
  > `branch_links.staff_reviewed` · `varchar` · opcional
- 🔗 repair_id <!-- orbit link: "#repair_requests", status: "chave estrangeira" -->
  > `branch_links.repair_id` · `int8` · obrigatória
  >
  > Aponta para [`repair_requests`](#repair_requests).
- ☑️ notice_code
  > `branch_links.notice_code` · `bool` · obrigatória
- 🧩 repair_total
  > `branch_links.repair_total` · `jsonb` · opcional
- 🧩 hold_created
  > `branch_links.hold_created` · `jsonb` · opcional
- 🔢 review_updated
  > `branch_links.review_updated` · `int4` · obrigatória
- ☑️ series_amount
  > `branch_links.series_amount` · `bool` · opcional
- 🔗 tags_id <!-- orbit link: "#tags", status: "chave estrangeira" -->
  > `branch_links.tags_id` · `int8` · obrigatória
  >
  > Aponta para [`tags`](#tags).
- 🔗 author_id_2 <!-- orbit link: "#author_links_2", status: "chave estrangeira" -->
  > `branch_links.author_id_2` · `int8` · obrigatória
  >
  > Aponta para [`author_links_2`](#author_links_2).
- 🔢 branch_active
  > `branch_links.branch_active` · `int4` · obrigatória
- 🔗 review_id <!-- orbit link: "#review_links", status: "chave estrangeira" -->
  > `branch_links.review_id` · `int8` · obrigatória
  >
  > Aponta para [`review_links`](#review_links).
- 🔢 shelf_status
  > `branch_links.shelf_status` · `int4` · obrigatória

### hold_links
<!-- orbit
id: "hold_links"
icon: "📇"
collapsed: true
-->
> `hold_links` · tabela de Unidades
>
> Uma linha por reserva, com 14 colunas e cerca de 194 mil linhas.
>
> **Pertence a** [`review_queues`](#review_queues) · [`order_items`](#order_items)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `hold_links.id` · `int8` · obrigatória
- 🔤 audit_external
  > `hold_links.audit_external` · `varchar` · obrigatória
- 🔢 review_ended
  > `hold_links.review_ended` · `int4` · opcional
- 🔢 copy_format
  > `hold_links.copy_format` · `int4` · opcional
- 🧩 notice_amount
  > `hold_links.notice_amount` · `jsonb` · obrigatória
- 🔤 vendor_state
  > `hold_links.vendor_state` · `varchar` · opcional
- 🧩 budget_amount
  > `hold_links.budget_amount` · `jsonb` · obrigatória
- 🕒 room_status
  > `hold_links.room_status` · `timestamp` · opcional
- 🔢 license_format
  > `hold_links.license_format` · `int4` · obrigatória
- 🔗 visit_id <!-- orbit link: "#visit_queues", status: "chave estrangeira" -->
  > `hold_links.visit_id` · `int8` · obrigatória
  >
  > Aponta para [`visit_queues`](#visit_queues).
- 🔤 repair_format
  > `hold_links.repair_format` · `varchar` · opcional
- 🔤 repair_public
  > `hold_links.repair_public` · `varchar` · opcional
- 🔢 copy_active
  > `hold_links.copy_active` · `int4` · obrigatória
- 🔤 branch_position
  > `hold_links.branch_position` · `varchar` · obrigatória

### hold_logs
<!-- orbit
id: "hold_logs"
icon: "📇"
collapsed: true
-->
> `hold_logs` · tabela de Unidades
>
> Uma linha por reserva, com 13 colunas e cerca de 798 mil linhas.
>
> **Pertence a** [`report_queues`](#report_queues) · [`tag_counts`](#tag_counts)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `hold_logs.id` · `int8` · obrigatória
- 🔗 copy_id <!-- orbit link: "#copy_history", status: "chave estrangeira" -->
  > `hold_logs.copy_id` · `int8` · obrigatória
  >
  > Aponta para [`copy_history`](#copy_history).
- 🔢 loan_total
  > `hold_logs.loan_total` · `int4` · opcional
- 🔤 order_reviewed
  > `hold_logs.order_reviewed` · `varchar` · opcional
- 🔤 hold_state
  > `hold_logs.hold_state` · `varchar` · obrigatória
- 🕒 report_format
  > `hold_logs.report_format` · `timestamp` · opcional
- 🔤 loan_title
  > `hold_logs.loan_title` · `varchar` · opcional
- 🕒 tag_source
  > `hold_logs.tag_source` · `timestamp` · obrigatória
- 🧩 card_updated
  > `hold_logs.card_updated` · `jsonb` · opcional
- 🕒 branch_archived
  > `hold_logs.branch_archived` · `timestamp` · obrigatória
- 🔢 license_external
  > `hold_logs.license_external` · `int4` · opcional
- 🔤 staff_public
  > `hold_logs.staff_public` · `varchar` · opcional
- 🔤 device_format
  > `hold_logs.device_format` · `varchar` · obrigatória

### holds
<!-- orbit
id: "holds"
icon: "🗃️"
collapsed: true
-->
> `holds` · tabela de Unidades
>
> Uma linha por reserva, com 7 colunas e cerca de 261 mil linhas.
>
> **Pertence a** [`fee_history`](#fee_history) · [`vendor_items`](#vendor_items) · [`notice_counts`](#notice_counts)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `holds.id` · `int8` · obrigatória
- 🕒 role_region
  > `holds.role_region` · `timestamp` · obrigatória
- ☑️ audit_kind
  > `holds.audit_kind` · `bool` · obrigatória
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
> `card_queues` · tabela de Unidades
>
> Uma linha por carteirinha, com 24 colunas e cerca de 413 mil linhas.
>
> **Pertence a** [`series_counts`](#series_counts) · [`report_logs_2`](#report_logs_2) · [`audit_queues`](#audit_queues)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `card_queues.id` · `int8` · obrigatória
- 🧩 fee_title
  > `card_queues.fee_title` · `jsonb` · obrigatória
- 🧩 visit_ended
  > `card_queues.visit_ended` · `jsonb` · opcional
- 🔗 book_id <!-- orbit link: "#book_settings", status: "chave estrangeira" -->
  > `card_queues.book_id` · `int8` · obrigatória
  >
  > Aponta para [`book_settings`](#book_settings).
- 🔢 payment_ended
  > `card_queues.payment_ended` · `int4` · opcional
- 🕒 room_active
  > `card_queues.room_active` · `timestamp` · obrigatória
- 🕒 staff_updated
  > `card_queues.staff_updated` · `timestamp` · opcional
- 🔗 budgets_id <!-- orbit link: "#budgets", status: "chave estrangeira" -->
  > `card_queues.budgets_id` · `int8` · obrigatória
  >
  > Aponta para [`budgets`](#budgets).
- 🧩 copy_state
  > `card_queues.copy_state` · `jsonb` · obrigatória
- 🕒 audit_total
  > `card_queues.audit_total` · `timestamp` · opcional
- 🕒 shelf_external
  > `card_queues.shelf_external` · `timestamp` · opcional
- ☑️ fee_code
  > `card_queues.fee_code` · `bool` · obrigatória
- 🔗 copy_id <!-- orbit link: "#copy_versions", status: "chave estrangeira" -->
  > `card_queues.copy_id` · `int8` · obrigatória
  >
  > Aponta para [`copy_versions`](#copy_versions).
- 🔢 staff_reviewed
  > `card_queues.staff_reviewed` · `int4` · opcional
- 🔢 visit_status
  > `card_queues.visit_status` · `int4` · obrigatória
- 🔢 vendor_format
  > `card_queues.vendor_format` · `int4` · opcional
- 🔤 member_active
  > `card_queues.member_active` · `varchar` · opcional
- ☑️ event_label
  > `card_queues.event_label` · `bool` · obrigatória
- 🔢 room_position
  > `card_queues.room_position` · `int4` · obrigatória
- 🔗 vendor_id <!-- orbit link: "#vendor_history", status: "chave estrangeira" -->
  > `card_queues.vendor_id` · `int8` · obrigatória
  >
  > Aponta para [`vendor_history`](#vendor_history).
- 🕒 order_external
  > `card_queues.order_external` · `timestamp` · opcional
- ☑️ fee_total
  > `card_queues.fee_total` · `bool` · opcional
- 🔤 repair_active
  > `card_queues.repair_active` · `varchar` · opcional
- 🕒 license_external
  > `card_queues.license_external` · `timestamp` · obrigatória

### hold_history
<!-- orbit
id: "hold_history"
icon: "🗃️"
collapsed: true
-->
> `hold_history` · tabela de Unidades
>
> Uma linha por reserva, com 14 colunas e cerca de 438 mil linhas.
>
> **Pertence a** [`review_items`](#review_items) · [`event_history`](#event_history) · [`repair_requests`](#repair_requests)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `hold_history.id` · `int8` · obrigatória
- ☑️ repair_active
  > `hold_history.repair_active` · `bool` · obrigatória
- 🔢 room_started
  > `hold_history.room_started` · `int4` · obrigatória
- 🔤 loan_amount
  > `hold_history.loan_amount` · `varchar` · opcional
- 🧩 branch_kind
  > `hold_history.branch_kind` · `jsonb` · obrigatória
- 🧩 visit_external
  > `hold_history.visit_external` · `jsonb` · obrigatória
- 🔢 member_kind
  > `hold_history.member_kind` · `int4` · obrigatória
- 🔢 series_label
  > `hold_history.series_label` · `int4` · obrigatória
- 🧩 donation_name
  > `hold_history.donation_name` · `jsonb` · obrigatória
- 🔢 visit_region
  > `hold_history.visit_region` · `int4` · obrigatória
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
> `room_requests` · tabela de Unidades
>
> Uma linha por sala, com 20 colunas e cerca de 874 mil linhas.
>
> **Pertence a** [`tag_settings_2`](#tag_settings_2)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `room_requests.id` · `int8` · obrigatória
- 🧩 branch_code
  > `room_requests.branch_code` · `jsonb` · opcional
- ☑️ ebook_language
  > `room_requests.ebook_language` · `bool` · obrigatória
- 🔗 card_id <!-- orbit link: "#card_links", status: "chave estrangeira" -->
  > `room_requests.card_id` · `int8` · obrigatória
  >
  > Aponta para [`card_links`](#card_links).
- 🔤 staff_name
  > `room_requests.staff_name` · `varchar` · obrigatória
- ☑️ event_state
  > `room_requests.event_state` · `bool` · opcional
- 🕒 event_amount
  > `room_requests.event_amount` · `timestamp` · opcional
- 🧩 staff_status
  > `room_requests.staff_status` · `jsonb` · obrigatória
- ☑️ report_external
  > `room_requests.report_external` · `bool` · opcional
- 🔤 book_position
  > `room_requests.book_position` · `varchar` · opcional
- 🕒 report_ended
  > `room_requests.report_ended` · `timestamp` · opcional
- 🔗 license_id <!-- orbit link: "#license_versions", status: "chave estrangeira" -->
  > `room_requests.license_id` · `int8` · obrigatória
  >
  > Aponta para [`license_versions`](#license_versions).
- 🕒 fee_total
  > `room_requests.fee_total` · `timestamp` · opcional
- 🔤 event_total
  > `room_requests.event_total` · `varchar` · obrigatória
- 🔗 device_id <!-- orbit link: "#device_items", status: "chave estrangeira" -->
  > `room_requests.device_id` · `int8` · obrigatória
  >
  > Aponta para [`device_items`](#device_items).
- 🧩 room_state
  > `room_requests.room_state` · `jsonb` · obrigatória
- 🔢 repair_name
  > `room_requests.repair_name` · `int4` · obrigatória
- 🔗 device_id_2 <!-- orbit link: "#device_items", status: "chave estrangeira" -->
  > `room_requests.device_id_2` · `int8` · obrigatória
  >
  > Aponta para [`device_items`](#device_items).
- 🔢 tag_ended
  > `room_requests.tag_ended` · `int4` · opcional
- ☑️ notice_region
  > `room_requests.notice_region` · `bool` · obrigatória

### review_links
<!-- orbit
id: "review_links"
icon: "🧮"
collapsed: true
-->
> `review_links` · tabela de Unidades
>
> Uma linha por resenha, com 20 colunas e cerca de 531 mil linhas.
>
> **Pertence a** [`report_items`](#report_items) · [`copy_items`](#copy_items) · [`order_items`](#order_items)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `review_links.id` · `int8` · obrigatória
- 🔗 member_id <!-- orbit link: "#member_versions", status: "chave estrangeira" -->
  > `review_links.member_id` · `int8` · obrigatória
  >
  > Aponta para [`member_versions`](#member_versions).
- 🔗 visits_id <!-- orbit link: "#visits", status: "chave estrangeira" -->
  > `review_links.visits_id` · `int8` · obrigatória
  >
  > Aponta para [`visits`](#visits).
- 🔗 series_id <!-- orbit link: "#series_links", status: "chave estrangeira" -->
  > `review_links.series_id` · `int8` · obrigatória
  >
  > Aponta para [`series_links`](#series_links).
- 🔗 license_id <!-- orbit link: "#license_queues", status: "chave estrangeira" -->
  > `review_links.license_id` · `int8` · obrigatória
  >
  > Aponta para [`license_queues`](#license_queues).
- 🔢 hold_notes
  > `review_links.hold_notes` · `int4` · obrigatória
- 🔗 copy_id <!-- orbit link: "#copy_versions", status: "chave estrangeira" -->
  > `review_links.copy_id` · `int8` · obrigatória
  >
  > Aponta para [`copy_versions`](#copy_versions).
- 🔗 visits_id_2 <!-- orbit link: "#visits", status: "chave estrangeira" -->
  > `review_links.visits_id_2` · `int8` · obrigatória
  >
  > Aponta para [`visits`](#visits).
- 🕒 card_position
  > `review_links.card_position` · `timestamp` · opcional
- 🕒 shelf_source
  > `review_links.shelf_source` · `timestamp` · opcional
- ☑️ room_active
  > `review_links.room_active` · `bool` · opcional
- 🕒 audit_started
  > `review_links.audit_started` · `timestamp` · obrigatória
- 🧩 ebook_title
  > `review_links.ebook_title` · `jsonb` · obrigatória
- 🕒 card_language
  > `review_links.card_language` · `timestamp` · obrigatória
- 🕒 notice_reviewed
  > `review_links.notice_reviewed` · `timestamp` · obrigatória
- ☑️ author_active
  > `review_links.author_active` · `bool` · opcional
- 🔗 loan_id <!-- orbit link: "#loan_logs_2", status: "chave estrangeira" -->
  > `review_links.loan_id` · `int8` · obrigatória
  >
  > Aponta para [`loan_logs_2`](#loan_logs_2).
- ☑️ notice_count
  > `review_links.notice_count` · `bool` · opcional
- 🔤 author_region
  > `review_links.author_region` · `varchar` · obrigatória
- 🔗 report_id <!-- orbit link: "#report_links", status: "chave estrangeira" -->
  > `review_links.report_id` · `int8` · obrigatória
  >
  > Aponta para [`report_links`](#report_links).

## 🗓️ Eventos
<!-- orbit
id: "area-events"
color: "#E5484D"
-->
> 10 tabelas. As maiores: [`author_links_2`](#author_links_2), [`shelf_queues`](#shelf_queues), [`fee_requests`](#fee_requests).
>
> **Relacionada a** [`order_logs`](#order_logs) · [`report_logs`](#report_logs) · [`ebook_requests`](#ebook_requests)

### author_links_2
<!-- orbit
id: "author_links_2"
icon: "🧷"
collapsed: true
-->
> `author_links_2` · tabela de Eventos
>
> Uma linha por autor, com 260 colunas e cerca de 52 mil linhas.
>
> **Pertence a** [`device_settings`](#device_settings)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `author_links_2.id` · `int8` · obrigatória
- 🔗 order_id <!-- orbit link: "#order_links", status: "chave estrangeira" -->
  > `author_links_2.order_id` · `int8` · obrigatória
  >
  > Aponta para [`order_links`](#order_links).
- 🔗 hold_id <!-- orbit link: "#hold_history", status: "chave estrangeira" -->
  > `author_links_2.hold_id` · `int8` · obrigatória
  >
  > Aponta para [`hold_history`](#hold_history).
- ☑️ repair_format
  > `author_links_2.repair_format` · `bool` · obrigatória
- 🕒 ebook_count
  > `author_links_2.ebook_count` · `timestamp` · opcional
- 🔢 copy_priority
  > `author_links_2.copy_priority` · `int4` · opcional
- ☑️ book_notes
  > `author_links_2.book_notes` · `bool` · opcional
- 🔗 review_id <!-- orbit link: "#review_counts", status: "chave estrangeira" -->
  > `author_links_2.review_id` · `int8` · obrigatória
  >
  > Aponta para [`review_counts`](#review_counts).
- 🔤 role_active
  > `author_links_2.role_active` · `varchar` · obrigatória
- 🕒 license_region
  > `author_links_2.license_region` · `timestamp` · opcional
- 🕒 vendor_region
  > `author_links_2.vendor_region` · `timestamp` · opcional
- 🧩 notice_kind
  > `author_links_2.notice_kind` · `jsonb` · opcional
- 🕒 payment_kind
  > `author_links_2.payment_kind` · `timestamp` · obrigatória
- 🧩 visit_public
  > `author_links_2.visit_public` · `jsonb` · opcional
- 🕒 donation_status
  > `author_links_2.donation_status` · `timestamp` · obrigatória
- 🔗 ebook_id <!-- orbit link: "#ebook_requests", status: "chave estrangeira" -->
  > `author_links_2.ebook_id` · `int8` · obrigatória
  >
  > Aponta para [`ebook_requests`](#ebook_requests).
- 🕒 event_active
  > `author_links_2.event_active` · `timestamp` · obrigatória
- 🔗 visits_id <!-- orbit link: "#visits", status: "chave estrangeira" -->
  > `author_links_2.visits_id` · `int8` · obrigatória
  >
  > Aponta para [`visits`](#visits).
- 🔗 series_id <!-- orbit link: "#series_requests", status: "chave estrangeira" -->
  > `author_links_2.series_id` · `int8` · obrigatória
  >
  > Aponta para [`series_requests`](#series_requests).
- 🧩 notice_total
  > `author_links_2.notice_total` · `jsonb` · opcional
- 🧩 payment_ended
  > `author_links_2.payment_ended` · `jsonb` · opcional
- 🔢 visit_count
  > `author_links_2.visit_count` · `int4` · obrigatória
- 🕒 donation_position
  > `author_links_2.donation_position` · `timestamp` · obrigatória
- 🔗 copy_id <!-- orbit link: "#copy_versions", status: "chave estrangeira" -->
  > `author_links_2.copy_id` · `int8` · obrigatória
  >
  > Aponta para [`copy_versions`](#copy_versions).
- ☑️ book_label
  > `author_links_2.book_label` · `bool` · obrigatória
- 🕒 fee_label
  > `author_links_2.fee_label` · `timestamp` · opcional
- 🔢 budget_status
  > `author_links_2.budget_status` · `int4` · obrigatória
- 🔢 copy_source
  > `author_links_2.copy_source` · `int4` · obrigatória
- 🔤 audit_notes
  > `author_links_2.audit_notes` · `varchar` · obrigatória
- ☑️ review_title
  > `author_links_2.review_title` · `bool` · opcional
- 🕒 author_title
  > `author_links_2.author_title` · `timestamp` · opcional
- ☑️ notice_started
  > `author_links_2.notice_started` · `bool` · obrigatória
- 🔢 event_state
  > `author_links_2.event_state` · `int4` · opcional
- 🧩 visit_total
  > `author_links_2.visit_total` · `jsonb` · obrigatória
- 🔗 license_id <!-- orbit link: "#license_queues_2", status: "chave estrangeira" -->
  > `author_links_2.license_id` · `int8` · obrigatória
  >
  > Aponta para [`license_queues_2`](#license_queues_2).
- 🔗 report_id <!-- orbit link: "#report_queues", status: "chave estrangeira" -->
  > `author_links_2.report_id` · `int8` · obrigatória
  >
  > Aponta para [`report_queues`](#report_queues).
- 🔗 room_id <!-- orbit link: "#room_settings", status: "chave estrangeira" -->
  > `author_links_2.room_id` · `int8` · obrigatória
  >
  > Aponta para [`room_settings`](#room_settings).
- ☑️ series_status
  > `author_links_2.series_status` · `bool` · opcional
- ☑️ loan_notes
  > `author_links_2.loan_notes` · `bool` · obrigatória
- 🔢 branch_amount
  > `author_links_2.branch_amount` · `int4` · obrigatória
- ☑️ branch_priority
  > `author_links_2.branch_priority` · `bool` · opcional
- 🔤 tag_title
  > `author_links_2.tag_title` · `varchar` · opcional
- 🔗 seriess_id <!-- orbit link: "#seriess", status: "chave estrangeira" -->
  > `author_links_2.seriess_id` · `int8` · obrigatória
  >
  > Aponta para [`seriess`](#seriess).
- 🕒 device_ended
  > `author_links_2.device_ended` · `timestamp` · obrigatória
- 🧩 event_format
  > `author_links_2.event_format` · `jsonb` · obrigatória
- 🔗 review_id_2 <!-- orbit link: "#review_links", status: "chave estrangeira" -->
  > `author_links_2.review_id_2` · `int8` · obrigatória
  >
  > Aponta para [`review_links`](#review_links).
- 🧩 license_status
  > `author_links_2.license_status` · `jsonb` · opcional
- 🧩 card_notes
  > `author_links_2.card_notes` · `jsonb` · obrigatória
- ☑️ hold_notes
  > `author_links_2.hold_notes` · `bool` · opcional
- 🔢 role_reviewed
  > `author_links_2.role_reviewed` · `int4` · opcional
- ☑️ budget_position
  > `author_links_2.budget_position` · `bool` · opcional
- 🕒 role_language
  > `author_links_2.role_language` · `timestamp` · obrigatória
- 🧩 review_title_2
  > `author_links_2.review_title_2` · `jsonb` · obrigatória
- 🔗 license_id_2 <!-- orbit link: "#license_logs_2", status: "chave estrangeira" -->
  > `author_links_2.license_id_2` · `int8` · obrigatória
  >
  > Aponta para [`license_logs_2`](#license_logs_2).
- ☑️ order_total
  > `author_links_2.order_total` · `bool` · opcional
- 🔗 tag_id <!-- orbit link: "#tag_requests", status: "chave estrangeira" -->
  > `author_links_2.tag_id` · `int8` · obrigatória
  >
  > Aponta para [`tag_requests`](#tag_requests).
- 🕒 loan_code
  > `author_links_2.loan_code` · `timestamp` · opcional
- 🔗 loan_id <!-- orbit link: "#loan_history", status: "chave estrangeira" -->
  > `author_links_2.loan_id` · `int8` · obrigatória
  >
  > Aponta para [`loan_history`](#loan_history).
- 🧩 notice_updated
  > `author_links_2.notice_updated` · `jsonb` · obrigatória
- 🕒 hold_reviewed
  > `author_links_2.hold_reviewed` · `timestamp` · opcional
- 🧩 staff_position
  > `author_links_2.staff_position` · `jsonb` · opcional
- 🕒 hold_notes_2
  > `author_links_2.hold_notes_2` · `timestamp` · obrigatória
- 🔗 holds_id <!-- orbit link: "#holds", status: "chave estrangeira" -->
  > `author_links_2.holds_id` · `int8` · obrigatória
  >
  > Aponta para [`holds`](#holds).
- 🔗 event_id <!-- orbit link: "#event_history", status: "chave estrangeira" -->
  > `author_links_2.event_id` · `int8` · obrigatória
  >
  > Aponta para [`event_history`](#event_history).
- ☑️ event_ended
  > `author_links_2.event_ended` · `bool` · opcional
- 🔗 ebook_id_2 <!-- orbit link: "#ebook_requests", status: "chave estrangeira" -->
  > `author_links_2.ebook_id_2` · `int8` · obrigatória
  >
  > Aponta para [`ebook_requests`](#ebook_requests).
- 🔤 tag_total
  > `author_links_2.tag_total` · `varchar` · opcional
- 🕒 member_status
  > `author_links_2.member_status` · `timestamp` · opcional
- ☑️ device_region
  > `author_links_2.device_region` · `bool` · opcional
- 🔗 notice_id <!-- orbit link: "#notice_counts", status: "chave estrangeira" -->
  > `author_links_2.notice_id` · `int8` · obrigatória
  >
  > Aponta para [`notice_counts`](#notice_counts).
- 🕒 device_source
  > `author_links_2.device_source` · `timestamp` · opcional
- 🔢 event_label
  > `author_links_2.event_label` · `int4` · opcional
- ☑️ budget_count
  > `author_links_2.budget_count` · `bool` · obrigatória
- 🔗 review_id_3 <!-- orbit link: "#review_counts", status: "chave estrangeira" -->
  > `author_links_2.review_id_3` · `int8` · obrigatória
  >
  > Aponta para [`review_counts`](#review_counts).
- 🔢 role_source
  > `author_links_2.role_source` · `int4` · obrigatória
- 🕒 series_status_2
  > `author_links_2.series_status_2` · `timestamp` · obrigatória
- 🔗 review_id_4 <!-- orbit link: "#review_counts", status: "chave estrangeira" -->
  > `author_links_2.review_id_4` · `int8` · obrigatória
  >
  > Aponta para [`review_counts`](#review_counts).
- 🔗 report_id_2 <!-- orbit link: "#report_queues_2", status: "chave estrangeira" -->
  > `author_links_2.report_id_2` · `int8` · obrigatória
  >
  > Aponta para [`report_queues_2`](#report_queues_2).
- 🧩 author_updated
  > `author_links_2.author_updated` · `jsonb` · obrigatória
- ☑️ repair_format_2
  > `author_links_2.repair_format_2` · `bool` · opcional
- 🕒 tag_status
  > `author_links_2.tag_status` · `timestamp` · obrigatória
- 🔢 budget_kind
  > `author_links_2.budget_kind` · `int4` · obrigatória
- 🕒 shelf_updated
  > `author_links_2.shelf_updated` · `timestamp` · obrigatória
- 🕒 shelf_language
  > `author_links_2.shelf_language` · `timestamp` · obrigatória
- 🕒 visit_created
  > `author_links_2.visit_created` · `timestamp` · opcional
- 🔤 review_count
  > `author_links_2.review_count` · `varchar` · obrigatória
- 🔗 ebook_id_3 <!-- orbit link: "#ebook_logs", status: "chave estrangeira" -->
  > `author_links_2.ebook_id_3` · `int8` · obrigatória
  >
  > Aponta para [`ebook_logs`](#ebook_logs).
- 🔗 branch_id <!-- orbit link: "#branch_links", status: "chave estrangeira" -->
  > `author_links_2.branch_id` · `int8` · obrigatória
  >
  > Aponta para [`branch_links`](#branch_links).
- 🔗 order_id_2 <!-- orbit link: "#order_items", status: "chave estrangeira" -->
  > `author_links_2.order_id_2` · `int8` · obrigatória
  >
  > Aponta para [`order_items`](#order_items).
- 🔤 member_priority
  > `author_links_2.member_priority` · `varchar` · obrigatória
- 🔢 repair_reviewed
  > `author_links_2.repair_reviewed` · `int4` · obrigatória
- ☑️ event_position
  > `author_links_2.event_position` · `bool` · opcional
- 🕒 license_state
  > `author_links_2.license_state` · `timestamp` · opcional
- 🔤 series_source
  > `author_links_2.series_source` · `varchar` · opcional
- ☑️ fee_started
  > `author_links_2.fee_started` · `bool` · obrigatória
- 🔢 copy_updated
  > `author_links_2.copy_updated` · `int4` · obrigatória
- 🔤 order_priority
  > `author_links_2.order_priority` · `varchar` · opcional
- 🔗 room_id_2 <!-- orbit link: "#room_logs", status: "chave estrangeira" -->
  > `author_links_2.room_id_2` · `int8` · obrigatória
  >
  > Aponta para [`room_logs`](#room_logs).
- 🔗 branch_id_2 <!-- orbit link: "#branch_links", status: "chave estrangeira" -->
  > `author_links_2.branch_id_2` · `int8` · obrigatória
  >
  > Aponta para [`branch_links`](#branch_links).
- 🔤 card_amount
  > `author_links_2.card_amount` · `varchar` · opcional
- 🧩 event_title
  > `author_links_2.event_title` · `jsonb` · obrigatória
- 🧩 book_public
  > `author_links_2.book_public` · `jsonb` · obrigatória
- 🔗 book_id <!-- orbit link: "#book_links", status: "chave estrangeira" -->
  > `author_links_2.book_id` · `int8` · obrigatória
  >
  > Aponta para [`book_links`](#book_links).
- 🔗 rooms_id <!-- orbit link: "#rooms", status: "chave estrangeira" -->
  > `author_links_2.rooms_id` · `int8` · obrigatória
  >
  > Aponta para [`rooms`](#rooms).
- 🕒 shelf_amount
  > `author_links_2.shelf_amount` · `timestamp` · obrigatória
- 🕒 event_priority
  > `author_links_2.event_priority` · `timestamp` · obrigatória
- 🔤 member_archived
  > `author_links_2.member_archived` · `varchar` · obrigatória
- 🔗 card_id <!-- orbit link: "#card_queues", status: "chave estrangeira" -->
  > `author_links_2.card_id` · `int8` · obrigatória
  >
  > Aponta para [`card_queues`](#card_queues).
- 🔢 event_reviewed
  > `author_links_2.event_reviewed` · `int4` · opcional
- 🔤 series_active
  > `author_links_2.series_active` · `varchar` · obrigatória
- 🧩 budget_ended
  > `author_links_2.budget_ended` · `jsonb` · opcional
- 🔗 order_id_3 <!-- orbit link: "#order_links", status: "chave estrangeira" -->
  > `author_links_2.order_id_3` · `int8` · obrigatória
  >
  > Aponta para [`order_links`](#order_links).
- 🔤 review_ended
  > `author_links_2.review_ended` · `varchar` · opcional
- 🕒 notice_reviewed
  > `author_links_2.notice_reviewed` · `timestamp` · opcional
- 🔗 order_id_4 <!-- orbit link: "#order_links", status: "chave estrangeira" -->
  > `author_links_2.order_id_4` · `int8` · obrigatória
  >
  > Aponta para [`order_links`](#order_links).
- ☑️ tag_priority
  > `author_links_2.tag_priority` · `bool` · opcional
- 🕒 budget_count_2
  > `author_links_2.budget_count_2` · `timestamp` · opcional
- 🔢 author_title_2
  > `author_links_2.author_title_2` · `int4` · opcional
- ☑️ loan_public
  > `author_links_2.loan_public` · `bool` · opcional
- 🔗 copy_id_2 <!-- orbit link: "#copy_counts", status: "chave estrangeira" -->
  > `author_links_2.copy_id_2` · `int8` · obrigatória
  >
  > Aponta para [`copy_counts`](#copy_counts).
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
  > `author_links_2.branch_format` · `bool` · obrigatória
- 🔢 hold_archived
  > `author_links_2.hold_archived` · `int4` · obrigatória
- 🔗 ebook_id_4 <!-- orbit link: "#ebook_logs", status: "chave estrangeira" -->
  > `author_links_2.ebook_id_4` · `int8` · obrigatória
  >
  > Aponta para [`ebook_logs`](#ebook_logs).
- 🔢 hold_position
  > `author_links_2.hold_position` · `int4` · obrigatória
- 🕒 role_updated
  > `author_links_2.role_updated` · `timestamp` · obrigatória
- 🔢 event_notes
  > `author_links_2.event_notes` · `int4` · opcional
- 🧩 hold_priority
  > `author_links_2.hold_priority` · `jsonb` · opcional
- 🧩 notice_reviewed_2
  > `author_links_2.notice_reviewed_2` · `jsonb` · obrigatória
- 🔢 report_state
  > `author_links_2.report_state` · `int4` · opcional
- ☑️ review_source
  > `author_links_2.review_source` · `bool` · opcional
- 🔢 budget_public
  > `author_links_2.budget_public` · `int4` · obrigatória
- 🔢 device_kind
  > `author_links_2.device_kind` · `int4` · opcional
- 🔤 event_amount
  > `author_links_2.event_amount` · `varchar` · obrigatória
- 🔤 payment_external
  > `author_links_2.payment_external` · `varchar` · obrigatória
- ☑️ report_notes
  > `author_links_2.report_notes` · `bool` · opcional
- ☑️ tag_position
  > `author_links_2.tag_position` · `bool` · opcional
- ☑️ room_priority
  > `author_links_2.room_priority` · `bool` · obrigatória
- 🕒 shelf_count
  > `author_links_2.shelf_count` · `timestamp` · obrigatória
- ☑️ review_priority
  > `author_links_2.review_priority` · `bool` · obrigatória
- 🔗 notice_id_2 <!-- orbit link: "#notice_counts", status: "chave estrangeira" -->
  > `author_links_2.notice_id_2` · `int8` · obrigatória
  >
  > Aponta para [`notice_counts`](#notice_counts).
- ☑️ payment_region
  > `author_links_2.payment_region` · `bool` · obrigatória
- ☑️ book_updated
  > `author_links_2.book_updated` · `bool` · opcional
- ☑️ review_status
  > `author_links_2.review_status` · `bool` · obrigatória
- 🔗 review_id_5 <!-- orbit link: "#review_logs", status: "chave estrangeira" -->
  > `author_links_2.review_id_5` · `int8` · obrigatória
  >
  > Aponta para [`review_logs`](#review_logs).
- 🔗 notice_id_3 <!-- orbit link: "#notice_counts", status: "chave estrangeira" -->
  > `author_links_2.notice_id_3` · `int8` · obrigatória
  >
  > Aponta para [`notice_counts`](#notice_counts).
- 🔗 member_id <!-- orbit link: "#member_history", status: "chave estrangeira" -->
  > `author_links_2.member_id` · `int8` · obrigatória
  >
  > Aponta para [`member_history`](#member_history).
- 🧩 notice_region
  > `author_links_2.notice_region` · `jsonb` · obrigatória
- 🔢 payment_region_2
  > `author_links_2.payment_region_2` · `int4` · obrigatória
- ☑️ vendor_kind
  > `author_links_2.vendor_kind` · `bool` · opcional
- 🧩 staff_public
  > `author_links_2.staff_public` · `jsonb` · obrigatória
- 🧩 shelf_started
  > `author_links_2.shelf_started` · `jsonb` · opcional
- 🔢 fee_format
  > `author_links_2.fee_format` · `int4` · opcional
- 🔤 visit_label
  > `author_links_2.visit_label` · `varchar` · opcional
- 🧩 room_name
  > `author_links_2.room_name` · `jsonb` · obrigatória
- 🧩 ebook_code
  > `author_links_2.ebook_code` · `jsonb` · obrigatória
- 🕒 ebook_count_2
  > `author_links_2.ebook_count_2` · `timestamp` · opcional
- ☑️ fee_kind
  > `author_links_2.fee_kind` · `bool` · opcional
- 🔢 role_status
  > `author_links_2.role_status` · `int4` · opcional
- 🔢 license_archived
  > `author_links_2.license_archived` · `int4` · obrigatória
- ☑️ copy_amount
  > `author_links_2.copy_amount` · `bool` · opcional
- 🔗 event_id_2 <!-- orbit link: "#event_history", status: "chave estrangeira" -->
  > `author_links_2.event_id_2` · `int8` · obrigatória
  >
  > Aponta para [`event_history`](#event_history).
- ☑️ ebook_updated
  > `author_links_2.ebook_updated` · `bool` · opcional
- 🔢 card_status
  > `author_links_2.card_status` · `int4` · obrigatória
- 🧩 tag_archived
  > `author_links_2.tag_archived` · `jsonb` · obrigatória
- 🔤 notice_code
  > `author_links_2.notice_code` · `varchar` · opcional
- 🔤 review_code
  > `author_links_2.review_code` · `varchar` · opcional
- 🧩 card_code
  > `author_links_2.card_code` · `jsonb` · obrigatória
- 🔤 book_active
  > `author_links_2.book_active` · `varchar` · obrigatória
- 🔢 license_started
  > `author_links_2.license_started` · `int4` · obrigatória
- 🔗 device_id <!-- orbit link: "#device_settings", status: "chave estrangeira" -->
  > `author_links_2.device_id` · `int8` · obrigatória
  >
  > Aponta para [`device_settings`](#device_settings).
- 🧩 report_language
  > `author_links_2.report_language` · `jsonb` · opcional
- 🔢 notice_amount
  > `author_links_2.notice_amount` · `int4` · obrigatória
- 🧩 event_source
  > `author_links_2.event_source` · `jsonb` · obrigatória
- 🧩 fee_position
  > `author_links_2.fee_position` · `jsonb` · obrigatória
- 🔤 member_public
  > `author_links_2.member_public` · `varchar` · opcional
- 🧩 notice_archived
  > `author_links_2.notice_archived` · `jsonb` · obrigatória
- 🧩 copy_kind
  > `author_links_2.copy_kind` · `jsonb` · obrigatória
- 🔢 order_active
  > `author_links_2.order_active` · `int4` · opcional
- 🧩 license_title
  > `author_links_2.license_title` · `jsonb` · opcional
- 🔤 event_name
  > `author_links_2.event_name` · `varchar` · opcional
- ☑️ vendor_source
  > `author_links_2.vendor_source` · `bool` · obrigatória
- 🔢 copy_name
  > `author_links_2.copy_name` · `int4` · opcional
- 🔗 visits_id_2 <!-- orbit link: "#visits", status: "chave estrangeira" -->
  > `author_links_2.visits_id_2` · `int8` · obrigatória
  >
  > Aponta para [`visits`](#visits).
- 🔢 order_title
  > `author_links_2.order_title` · `int4` · opcional
- ☑️ room_created
  > `author_links_2.room_created` · `bool` · opcional
- 🕒 branch_language
  > `author_links_2.branch_language` · `timestamp` · opcional
- 🕒 report_reviewed
  > `author_links_2.report_reviewed` · `timestamp` · obrigatória
- 🔤 hold_region
  > `author_links_2.hold_region` · `varchar` · opcional
- 🕒 series_total
  > `author_links_2.series_total` · `timestamp` · obrigatória
- ☑️ report_notes_2
  > `author_links_2.report_notes_2` · `bool` · opcional
- ☑️ payment_code
  > `author_links_2.payment_code` · `bool` · opcional
- 🔗 donation_id <!-- orbit link: "#donation_history", status: "chave estrangeira" -->
  > `author_links_2.donation_id` · `int8` · obrigatória
  >
  > Aponta para [`donation_history`](#donation_history).
- 🔤 payment_language
  > `author_links_2.payment_language` · `varchar` · opcional
- ☑️ member_format_2
  > `author_links_2.member_format_2` · `bool` · obrigatória
- 🔤 order_external
  > `author_links_2.order_external` · `varchar` · obrigatória
- 🕒 review_priority_2
  > `author_links_2.review_priority_2` · `timestamp` · opcional
- ☑️ fee_status
  > `author_links_2.fee_status` · `bool` · obrigatória
- ☑️ series_status_3
  > `author_links_2.series_status_3` · `bool` · obrigatória
- 🕒 card_status_2
  > `author_links_2.card_status_2` · `timestamp` · opcional
- 🕒 staff_active
  > `author_links_2.staff_active` · `timestamp` · obrigatória
- 🕒 series_language
  > `author_links_2.series_language` · `timestamp` · opcional
- 🔤 card_total
  > `author_links_2.card_total` · `varchar` · obrigatória
- 🧩 ebook_language
  > `author_links_2.ebook_language` · `jsonb` · opcional
- 🔗 audit_id <!-- orbit link: "#audit_links", status: "chave estrangeira" -->
  > `author_links_2.audit_id` · `int8` · obrigatória
  >
  > Aponta para [`audit_links`](#audit_links).
- 🔤 series_status_4
  > `author_links_2.series_status_4` · `varchar` · obrigatória
- 🔤 ebook_priority
  > `author_links_2.ebook_priority` · `varchar` · opcional
- 🔗 license_id_3 <!-- orbit link: "#license_versions", status: "chave estrangeira" -->
  > `author_links_2.license_id_3` · `int8` · obrigatória
  >
  > Aponta para [`license_versions`](#license_versions).
- 🕒 tag_count
  > `author_links_2.tag_count` · `timestamp` · obrigatória
- 🔤 tag_public
  > `author_links_2.tag_public` · `varchar` · obrigatória
- 🕒 order_status
  > `author_links_2.order_status` · `timestamp` · opcional
- 🔗 author_id <!-- orbit link: "#author_links_2", status: "chave estrangeira" -->
  > `author_links_2.author_id` · `int8` · obrigatória
  >
  > Aponta para [`author_links_2`](#author_links_2).
- 🧩 author_total
  > `author_links_2.author_total` · `jsonb` · obrigatória
- 🔢 order_label
  > `author_links_2.order_label` · `int4` · obrigatória
- 🔢 payment_updated
  > `author_links_2.payment_updated` · `int4` · obrigatória
- ☑️ branch_code
  > `author_links_2.branch_code` · `bool` · obrigatória
- ☑️ repair_created
  > `author_links_2.repair_created` · `bool` · opcional
- 🕒 report_started
  > `author_links_2.report_started` · `timestamp` · obrigatória
- 🔢 author_status
  > `author_links_2.author_status` · `int4` · obrigatória
- 🔤 member_kind
  > `author_links_2.member_kind` · `varchar` · obrigatória
- 🧩 visit_ended
  > `author_links_2.visit_ended` · `jsonb` · opcional
- 🧩 role_status_2
  > `author_links_2.role_status_2` · `jsonb` · obrigatória
- 🧩 staff_amount
  > `author_links_2.staff_amount` · `jsonb` · opcional
- 🔢 hold_public
  > `author_links_2.hold_public` · `int4` · obrigatória
- 🧩 notice_title
  > `author_links_2.notice_title` · `jsonb` · obrigatória
- 🔗 budget_id <!-- orbit link: "#budget_queues", status: "chave estrangeira" -->
  > `author_links_2.budget_id` · `int8` · obrigatória
  >
  > Aponta para [`budget_queues`](#budget_queues).
- 🔤 visit_format
  > `author_links_2.visit_format` · `varchar` · opcional
- 🧩 visit_region
  > `author_links_2.visit_region` · `jsonb` · obrigatória
- ☑️ notice_archived_2
  > `author_links_2.notice_archived_2` · `bool` · opcional
- 🔤 event_status
  > `author_links_2.event_status` · `varchar` · obrigatória
- 🔢 fee_name
  > `author_links_2.fee_name` · `int4` · obrigatória
- 🔗 licenses_id <!-- orbit link: "#licenses", status: "chave estrangeira" -->
  > `author_links_2.licenses_id` · `int8` · obrigatória
  >
  > Aponta para [`licenses`](#licenses).
- 🔤 review_label
  > `author_links_2.review_label` · `varchar` · opcional
- 🕒 series_reviewed
  > `author_links_2.series_reviewed` · `timestamp` · obrigatória
- 🕒 event_reviewed_2
  > `author_links_2.event_reviewed_2` · `timestamp` · opcional
- 🔗 budget_id_2 <!-- orbit link: "#budget_settings_2", status: "chave estrangeira" -->
  > `author_links_2.budget_id_2` · `int8` · obrigatória
  >
  > Aponta para [`budget_settings_2`](#budget_settings_2).
- 🕒 ebook_created
  > `author_links_2.ebook_created` · `timestamp` · opcional
- 🔗 audit_id_2 <!-- orbit link: "#audit_settings", status: "chave estrangeira" -->
  > `author_links_2.audit_id_2` · `int8` · obrigatória
  >
  > Aponta para [`audit_settings`](#audit_settings).
- 🔤 author_created
  > `author_links_2.author_created` · `varchar` · obrigatória
- 🔤 event_external
  > `author_links_2.event_external` · `varchar` · obrigatória
- 🔤 series_updated
  > `author_links_2.series_updated` · `varchar` · opcional
- 🔤 loan_created
  > `author_links_2.loan_created` · `varchar` · obrigatória
- 🧩 role_status_3
  > `author_links_2.role_status_3` · `jsonb` · obrigatória
- 🧩 book_started
  > `author_links_2.book_started` · `jsonb` · obrigatória
- 🧩 role_active_2
  > `author_links_2.role_active_2` · `jsonb` · obrigatória
- 🕒 order_reviewed
  > `author_links_2.order_reviewed` · `timestamp` · opcional
- 🔤 card_reviewed
  > `author_links_2.card_reviewed` · `varchar` · opcional
- ☑️ event_position_2
  > `author_links_2.event_position_2` · `bool` · opcional
- 🔢 review_label_2
  > `author_links_2.review_label_2` · `int4` · opcional
- 🔤 role_format
  > `author_links_2.role_format` · `varchar` · obrigatória
- 🕒 payment_region_3
  > `author_links_2.payment_region_3` · `timestamp` · obrigatória
- 🕒 ebook_position
  > `author_links_2.ebook_position` · `timestamp` · obrigatória
- 🧩 tag_archived_2
  > `author_links_2.tag_archived_2` · `jsonb` · obrigatória
- 🧩 shelf_external
  > `author_links_2.shelf_external` · `jsonb` · obrigatória
- 🔢 report_region
  > `author_links_2.report_region` · `int4` · opcional
- ☑️ vendor_label
  > `author_links_2.vendor_label` · `bool` · obrigatória

### shelf_queues
<!-- orbit
id: "shelf_queues"
icon: "📇"
collapsed: true
-->
> `shelf_queues` · tabela de Eventos
>
> Uma linha por estante, com 21 colunas e cerca de 806 mil linhas.
>
> **Pertence a** [`visit_queues`](#visit_queues) · [`book_items`](#book_items)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `shelf_queues.id` · `int8` · obrigatória
- ☑️ loan_ended
  > `shelf_queues.loan_ended` · `bool` · opcional
- 🕒 event_active
  > `shelf_queues.event_active` · `timestamp` · opcional
- 🔗 report_id <!-- orbit link: "#report_logs", status: "chave estrangeira" -->
  > `shelf_queues.report_id` · `int8` · obrigatória
  >
  > Aponta para [`report_logs`](#report_logs).
- 🔗 donation_id <!-- orbit link: "#donation_queues", status: "chave estrangeira" -->
  > `shelf_queues.donation_id` · `int8` · obrigatória
  >
  > Aponta para [`donation_queues`](#donation_queues).
- 🕒 copy_code
  > `shelf_queues.copy_code` · `timestamp` · obrigatória
- 🔗 report_id_2 <!-- orbit link: "#report_queues", status: "chave estrangeira" -->
  > `shelf_queues.report_id_2` · `int8` · obrigatória
  >
  > Aponta para [`report_queues`](#report_queues).
- 🔢 series_reviewed
  > `shelf_queues.series_reviewed` · `int4` · obrigatória
- ☑️ donation_active
  > `shelf_queues.donation_active` · `bool` · obrigatória
- 🔤 review_kind
  > `shelf_queues.review_kind` · `varchar` · obrigatória
- ☑️ shelf_priority
  > `shelf_queues.shelf_priority` · `bool` · obrigatória
- 🕒 payment_status
  > `shelf_queues.payment_status` · `timestamp` · opcional
- ☑️ order_language
  > `shelf_queues.order_language` · `bool` · obrigatória
- 🔗 staff_id <!-- orbit link: "#staff_counts", status: "chave estrangeira" -->
  > `shelf_queues.staff_id` · `int8` · obrigatória
  >
  > Aponta para [`staff_counts`](#staff_counts).
- 🔗 loan_id <!-- orbit link: "#loan_settings", status: "chave estrangeira" -->
  > `shelf_queues.loan_id` · `int8` · obrigatória
  >
  > Aponta para [`loan_settings`](#loan_settings).
- 🧩 member_external
  > `shelf_queues.member_external` · `jsonb` · opcional
- 🔤 event_name
  > `shelf_queues.event_name` · `varchar` · obrigatória
- 🔗 fee_id <!-- orbit link: "#fee_requests", status: "chave estrangeira" -->
  > `shelf_queues.fee_id` · `int8` · obrigatória
  >
  > Aponta para [`fee_requests`](#fee_requests).
- 🧩 hold_name
  > `shelf_queues.hold_name` · `jsonb` · opcional
- 🔤 copy_started
  > `shelf_queues.copy_started` · `varchar` · opcional
- 🔗 copy_id <!-- orbit link: "#copy_items", status: "chave estrangeira" -->
  > `shelf_queues.copy_id` · `int8` · obrigatória
  >
  > Aponta para [`copy_items`](#copy_items).

### fee_requests
<!-- orbit
id: "fee_requests"
icon: "📗"
collapsed: true
-->
> `fee_requests` · tabela de Eventos
>
> Uma linha por multa, com 23 colunas e cerca de 202 mil linhas.
>
> **Pertence a** [`loan_counts`](#loan_counts) · [`review_queues`](#review_queues)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `fee_requests.id` · `int8` · obrigatória
- 🔢 tag_external
  > `fee_requests.tag_external` · `int4` · obrigatória
- 🔗 license_id <!-- orbit link: "#license_queues", status: "chave estrangeira" -->
  > `fee_requests.license_id` · `int8` · obrigatória
  >
  > Aponta para [`license_queues`](#license_queues).
- 🔤 license_state
  > `fee_requests.license_state` · `varchar` · obrigatória
- 🧩 hold_active
  > `fee_requests.hold_active` · `jsonb` · obrigatória
- ☑️ event_archived
  > `fee_requests.event_archived` · `bool` · obrigatória
- 🔢 room_title
  > `fee_requests.room_title` · `int4` · obrigatória
- 🕒 payment_position
  > `fee_requests.payment_position` · `timestamp` · opcional
- 🕒 repair_code
  > `fee_requests.repair_code` · `timestamp` · opcional
- 🔗 payment_id <!-- orbit link: "#payment_settings", status: "chave estrangeira" -->
  > `fee_requests.payment_id` · `int8` · obrigatória
  >
  > Aponta para [`payment_settings`](#payment_settings).
- 🧩 donation_label
  > `fee_requests.donation_label` · `jsonb` · obrigatória
- 🧩 loan_public
  > `fee_requests.loan_public` · `jsonb` · opcional
- 🕒 payment_public
  > `fee_requests.payment_public` · `timestamp` · obrigatória
- 🕒 shelf_count
  > `fee_requests.shelf_count` · `timestamp` · opcional
- 🔗 series_id <!-- orbit link: "#series_history", status: "chave estrangeira" -->
  > `fee_requests.series_id` · `int8` · obrigatória
  >
  > Aponta para [`series_history`](#series_history).
- ☑️ tag_priority
  > `fee_requests.tag_priority` · `bool` · opcional
- ☑️ report_region
  > `fee_requests.report_region` · `bool` · obrigatória
- 🔗 vendor_id <!-- orbit link: "#vendor_items", status: "chave estrangeira" -->
  > `fee_requests.vendor_id` · `int8` · obrigatória
  >
  > Aponta para [`vendor_items`](#vendor_items).
- ☑️ repair_region
  > `fee_requests.repair_region` · `bool` · opcional
- 🔢 card_notes
  > `fee_requests.card_notes` · `int4` · obrigatória
- 🔤 license_status
  > `fee_requests.license_status` · `varchar` · opcional
- 🔗 repair_id <!-- orbit link: "#repair_requests", status: "chave estrangeira" -->
  > `fee_requests.repair_id` · `int8` · obrigatória
  >
  > Aponta para [`repair_requests`](#repair_requests).
- 🧩 room_active
  > `fee_requests.room_active` · `jsonb` · opcional

### event_history
<!-- orbit
id: "event_history"
icon: "📇"
collapsed: true
-->
> `event_history` · tabela de Eventos
>
> Uma linha por evento, com 21 colunas e cerca de 561 mil linhas.
>
> **Pertence a** [`card_links_2`](#card_links_2)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `event_history.id` · `int8` · obrigatória
- 🕒 device_title
  > `event_history.device_title` · `timestamp` · opcional
- ☑️ author_kind
  > `event_history.author_kind` · `bool` · opcional
- ☑️ review_reviewed
  > `event_history.review_reviewed` · `bool` · obrigatória
- 🕒 author_status
  > `event_history.author_status` · `timestamp` · opcional
- 🧩 ebook_amount
  > `event_history.ebook_amount` · `jsonb` · obrigatória
- 🔢 notice_format
  > `event_history.notice_format` · `int4` · obrigatória
- ☑️ branch_reviewed
  > `event_history.branch_reviewed` · `bool` · opcional
- 🧩 role_reviewed
  > `event_history.role_reviewed` · `jsonb` · obrigatória
- 🔤 role_external
  > `event_history.role_external` · `varchar` · obrigatória
- 🔢 branch_label
  > `event_history.branch_label` · `int4` · obrigatória
- 🔗 series_id <!-- orbit link: "#series_history", status: "chave estrangeira" -->
  > `event_history.series_id` · `int8` · obrigatória
  >
  > Aponta para [`series_history`](#series_history).
- 🔗 copy_id <!-- orbit link: "#copy_items", status: "chave estrangeira" -->
  > `event_history.copy_id` · `int8` · obrigatória
  >
  > Aponta para [`copy_items`](#copy_items).
- 🔗 shelfs_id <!-- orbit link: "#shelfs", status: "chave estrangeira" -->
  > `event_history.shelfs_id` · `int8` · obrigatória
  >
  > Aponta para [`shelfs`](#shelfs).
- 🔢 notice_source
  > `event_history.notice_source` · `int4` · obrigatória
- 🔤 book_source
  > `event_history.book_source` · `varchar` · opcional
- 🧩 repair_position
  > `event_history.repair_position` · `jsonb` · obrigatória
- 🔢 payment_priority
  > `event_history.payment_priority` · `int4` · opcional
- 🔤 copy_language
  > `event_history.copy_language` · `varchar` · opcional
- 🕒 staff_format
  > `event_history.staff_format` · `timestamp` · opcional
- 🔤 audit_external
  > `event_history.audit_external` · `varchar` · obrigatória

### audit_links_3
<!-- orbit
id: "audit_links_3"
icon: "🧮"
collapsed: true
-->
> `audit_links_3` · tabela de Eventos
>
> Uma linha por auditoria, com 13 colunas e cerca de 462 mil linhas.
>
> **Pertence a** [`holds`](#holds) · [`report_queues_2`](#report_queues_2) · [`visit_items`](#visit_items)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `audit_links_3.id` · `int8` · obrigatória
- 🧩 book_source
  > `audit_links_3.book_source` · `jsonb` · obrigatória
- ☑️ card_kind
  > `audit_links_3.card_kind` · `bool` · obrigatória
- 🔢 loan_external
  > `audit_links_3.loan_external` · `int4` · opcional
- 🔢 author_title
  > `audit_links_3.author_title` · `int4` · obrigatória
- 🕒 vendor_amount
  > `audit_links_3.vendor_amount` · `timestamp` · opcional
- ☑️ event_title
  > `audit_links_3.event_title` · `bool` · obrigatória
- ☑️ fee_source
  > `audit_links_3.fee_source` · `bool` · obrigatória
- 🔗 ebook_id <!-- orbit link: "#ebook_queues", status: "chave estrangeira" -->
  > `audit_links_3.ebook_id` · `int8` · obrigatória
  >
  > Aponta para [`ebook_queues`](#ebook_queues).
- 🕒 review_kind
  > `audit_links_3.review_kind` · `timestamp` · obrigatória
- 🧩 shelf_active
  > `audit_links_3.shelf_active` · `jsonb` · obrigatória
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
> `report_logs_2` · tabela de Eventos
>
> Uma linha por relatório, com 22 colunas e cerca de 284 mil linhas.
>
> **Pertence a** [`ebook_requests`](#ebook_requests) · [`staff_settings`](#staff_settings) · [`loan_history`](#loan_history) · [`author_links_2`](#author_links_2)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `report_logs_2.id` · `int8` · obrigatória
- 🕒 book_kind
  > `report_logs_2.book_kind` · `timestamp` · opcional
- 🔗 visit_id <!-- orbit link: "#visit_links", status: "chave estrangeira" -->
  > `report_logs_2.visit_id` · `int8` · obrigatória
  >
  > Aponta para [`visit_links`](#visit_links).
- 🕒 review_label
  > `report_logs_2.review_label` · `timestamp` · obrigatória
- 🧩 loan_active
  > `report_logs_2.loan_active` · `jsonb` · opcional
- 🔗 author_id <!-- orbit link: "#author_links_2", status: "chave estrangeira" -->
  > `report_logs_2.author_id` · `int8` · obrigatória
  >
  > Aponta para [`author_links_2`](#author_links_2).
- 🔤 fee_public
  > `report_logs_2.fee_public` · `varchar` · obrigatória
- 🔗 branchs_id <!-- orbit link: "#branchs", status: "chave estrangeira" -->
  > `report_logs_2.branchs_id` · `int8` · obrigatória
  >
  > Aponta para [`branchs`](#branchs).
- 🧩 ebook_position
  > `report_logs_2.ebook_position` · `jsonb` · obrigatória
- ☑️ review_started
  > `report_logs_2.review_started` · `bool` · obrigatória
- 🧩 hold_format
  > `report_logs_2.hold_format` · `jsonb` · obrigatória
- 🔢 budget_region
  > `report_logs_2.budget_region` · `int4` · opcional
- 🔢 staff_name
  > `report_logs_2.staff_name` · `int4` · obrigatória
- 🕒 branch_ended
  > `report_logs_2.branch_ended` · `timestamp` · opcional
- 🔢 payment_archived
  > `report_logs_2.payment_archived` · `int4` · obrigatória
- 🕒 fee_reviewed
  > `report_logs_2.fee_reviewed` · `timestamp` · opcional
- 🕒 branch_status
  > `report_logs_2.branch_status` · `timestamp` · obrigatória
- 🕒 payment_amount
  > `report_logs_2.payment_amount` · `timestamp` · obrigatória
- ☑️ branch_public
  > `report_logs_2.branch_public` · `bool` · obrigatória
- 🔗 shelf_id <!-- orbit link: "#shelf_counts", status: "chave estrangeira" -->
  > `report_logs_2.shelf_id` · `int8` · obrigatória
  >
  > Aponta para [`shelf_counts`](#shelf_counts).
- ☑️ device_total
  > `report_logs_2.device_total` · `bool` · obrigatória
- 🔗 vendor_id <!-- orbit link: "#vendor_items", status: "chave estrangeira" -->
  > `report_logs_2.vendor_id` · `int8` · obrigatória
  >
  > Aponta para [`vendor_items`](#vendor_items).

### audit_queues
<!-- orbit
id: "audit_queues"
icon: "🗃️"
collapsed: true
-->
> `audit_queues` · tabela de Eventos
>
> Uma linha por auditoria, com 14 colunas e cerca de 478 mil linhas.
>
> **Pertence a** [`report_items`](#report_items) · [`notice_counts_2`](#notice_counts_2) · [`loan_history`](#loan_history)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `audit_queues.id` · `int8` · obrigatória
- 🕒 branch_name
  > `audit_queues.branch_name` · `timestamp` · obrigatória
- 🔗 loan_id <!-- orbit link: "#loan_counts", status: "chave estrangeira" -->
  > `audit_queues.loan_id` · `int8` · obrigatória
  >
  > Aponta para [`loan_counts`](#loan_counts).
- 🔗 budget_id <!-- orbit link: "#budget_queues", status: "chave estrangeira" -->
  > `audit_queues.budget_id` · `int8` · obrigatória
  >
  > Aponta para [`budget_queues`](#budget_queues).
- 🧩 device_code
  > `audit_queues.device_code` · `jsonb` · obrigatória
- ☑️ fee_amount
  > `audit_queues.fee_amount` · `bool` · opcional
- ☑️ author_code
  > `audit_queues.author_code` · `bool` · opcional
- 🔢 notice_public
  > `audit_queues.notice_public` · `int4` · opcional
- ☑️ staff_started
  > `audit_queues.staff_started` · `bool` · obrigatória
- 🕒 card_source
  > `audit_queues.card_source` · `timestamp` · obrigatória
- 🧩 role_created
  > `audit_queues.role_created` · `jsonb` · obrigatória
- 🧩 notice_amount
  > `audit_queues.notice_amount` · `jsonb` · opcional
- 🔗 event_id <!-- orbit link: "#event_queues", status: "chave estrangeira" -->
  > `audit_queues.event_id` · `int8` · obrigatória
  >
  > Aponta para [`event_queues`](#event_queues).
- 🔢 branch_count
  > `audit_queues.branch_count` · `int4` · obrigatória

### ebook_versions
<!-- orbit
id: "ebook_versions"
icon: "🧮"
collapsed: true
-->
> `ebook_versions` · tabela de Eventos
>
> Uma linha por livro digital, com 15 colunas e cerca de 874 mil linhas.
>
> **Pertence a** [`visit_items`](#visit_items) · [`order_versions`](#order_versions) · [`device_settings`](#device_settings)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `ebook_versions.id` · `int8` · obrigatória
- 🔢 series_external
  > `ebook_versions.series_external` · `int4` · obrigatória
- ☑️ review_priority
  > `ebook_versions.review_priority` · `bool` · obrigatória
- ☑️ role_updated
  > `ebook_versions.role_updated` · `bool` · opcional
- 🔢 vendor_priority
  > `ebook_versions.vendor_priority` · `int4` · obrigatória
- 🕒 copy_state
  > `ebook_versions.copy_state` · `timestamp` · opcional
- ☑️ audit_amount
  > `ebook_versions.audit_amount` · `bool` · opcional
- 🔗 staff_id <!-- orbit link: "#staff_counts", status: "chave estrangeira" -->
  > `ebook_versions.staff_id` · `int8` · obrigatória
  >
  > Aponta para [`staff_counts`](#staff_counts).
- 🔗 loan_id <!-- orbit link: "#loan_logs_2", status: "chave estrangeira" -->
  > `ebook_versions.loan_id` · `int8` · obrigatória
  >
  > Aponta para [`loan_logs_2`](#loan_logs_2).
- 🧩 vendor_label
  > `ebook_versions.vendor_label` · `jsonb` · opcional
- 🕒 donation_amount
  > `ebook_versions.donation_amount` · `timestamp` · opcional
- 🔗 book_id <!-- orbit link: "#book_items", status: "chave estrangeira" -->
  > `ebook_versions.book_id` · `int8` · obrigatória
  >
  > Aponta para [`book_items`](#book_items).
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
> `loan_settings` · tabela de Eventos
>
> Uma linha por empréstimo, com 12 colunas e cerca de 314 mil linhas.
>
> **Pertence a** [`role_counts`](#role_counts)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `loan_settings.id` · `int8` · obrigatória
- 🕒 card_name
  > `loan_settings.card_name` · `timestamp` · obrigatória
- 🔗 order_id <!-- orbit link: "#order_logs", status: "chave estrangeira" -->
  > `loan_settings.order_id` · `int8` · obrigatória
  >
  > Aponta para [`order_logs`](#order_logs).
- 🧩 staff_notes
  > `loan_settings.staff_notes` · `jsonb` · opcional
- ☑️ license_started
  > `loan_settings.license_started` · `bool` · obrigatória
- 🕒 shelf_total
  > `loan_settings.shelf_total` · `timestamp` · obrigatória
- 🔢 book_amount
  > `loan_settings.book_amount` · `int4` · obrigatória
- ☑️ staff_region
  > `loan_settings.staff_region` · `bool` · obrigatória
- 🧩 review_ended
  > `loan_settings.review_ended` · `jsonb` · obrigatória
- 🧩 fee_reviewed
  > `loan_settings.fee_reviewed` · `jsonb` · obrigatória
- 🔗 visit_id <!-- orbit link: "#visit_links", status: "chave estrangeira" -->
  > `loan_settings.visit_id` · `int8` · obrigatória
  >
  > Aponta para [`visit_links`](#visit_links).
- 🕒 copy_region
  > `loan_settings.copy_region` · `timestamp` · opcional

### tag_requests
<!-- orbit
id: "tag_requests"
icon: "🧮"
collapsed: true
-->
> `tag_requests` · tabela de Eventos
>
> Uma linha por etiqueta, com 10 colunas e cerca de 466 mil linhas.
>
> **Pertence a** [`staff_settings`](#staff_settings) · [`author_links`](#author_links) · [`budget_settings_2`](#budget_settings_2) · [`ebook_queues`](#ebook_queues)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `tag_requests.id` · `int8` · obrigatória
- 🔤 license_archived
  > `tag_requests.license_archived` · `varchar` · obrigatória
- 🔤 order_state
  > `tag_requests.order_state` · `varchar` · obrigatória
- ☑️ tag_label
  > `tag_requests.tag_label` · `bool` · opcional
- 🕒 device_language
  > `tag_requests.device_language` · `timestamp` · opcional
- 🕒 book_started
  > `tag_requests.book_started` · `timestamp` · obrigatória
- 🔢 event_priority
  > `tag_requests.event_priority` · `int4` · obrigatória
- ☑️ vendor_total
  > `tag_requests.vendor_total` · `bool` · opcional
- 🔢 card_count
  > `tag_requests.card_count` · `int4` · opcional
- 🧩 role_notes
  > `tag_requests.role_notes` · `jsonb` · obrigatória

## 💳 Multas e pagamentos
<!-- orbit
id: "area-fees-payments"
color: "#D97706"
-->
> 13 tabelas. As maiores: [`license_versions`](#license_versions), [`member_counts_2`](#member_counts_2), [`review_versions`](#review_versions).
>
> **Relacionada a** [`license_versions`](#license_versions) · [`room_logs`](#room_logs) · [`audit_links`](#audit_links)

### license_versions
<!-- orbit
id: "license_versions"
icon: "📗"
collapsed: true
-->
> `license_versions` · tabela de Multas e pagamentos
>
> Uma linha por licença, com 10 colunas e cerca de 440 mil linhas.
>
> **Pertence a** [`review_logs`](#review_logs) · [`staff_counts`](#staff_counts)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `license_versions.id` · `int8` · obrigatória
- 🔢 card_public
  > `license_versions.card_public` · `int4` · obrigatória
- 🔗 holds_id <!-- orbit link: "#holds_2", status: "chave estrangeira" -->
  > `license_versions.holds_id` · `int8` · obrigatória
  >
  > Aponta para [`holds_2`](#holds_2).
- 🔢 visit_archived
  > `license_versions.visit_archived` · `int4` · obrigatória
- 🔢 copy_title
  > `license_versions.copy_title` · `int4` · obrigatória
- ☑️ room_language
  > `license_versions.room_language` · `bool` · obrigatória
- 🔗 license_id <!-- orbit link: "#license_logs_2", status: "chave estrangeira" -->
  > `license_versions.license_id` · `int8` · obrigatória
  >
  > Aponta para [`license_logs_2`](#license_logs_2).
- 🧩 series_priority
  > `license_versions.series_priority` · `jsonb` · obrigatória
- 🔢 event_started
  > `license_versions.event_started` · `int4` · obrigatória
- 🔤 shelf_amount
  > `license_versions.shelf_amount` · `varchar` · opcional

### member_counts_2
<!-- orbit
id: "member_counts_2"
icon: "📇"
collapsed: true
-->
> `member_counts_2` · tabela de Multas e pagamentos
>
> Uma linha por leitor, com 23 colunas e cerca de 525 mil linhas.
>
> **Pertence a** [`hold_history`](#hold_history) · [`fee_history`](#fee_history)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `member_counts_2.id` · `int8` · obrigatória
- 🕒 donation_archived
  > `member_counts_2.donation_archived` · `timestamp` · obrigatória
- 🔤 author_external
  > `member_counts_2.author_external` · `varchar` · opcional
- 🕒 review_count
  > `member_counts_2.review_count` · `timestamp` · obrigatória
- 🔤 book_code
  > `member_counts_2.book_code` · `varchar` · obrigatória
- 🧩 order_kind
  > `member_counts_2.order_kind` · `jsonb` · obrigatória
- 🔤 vendor_external
  > `member_counts_2.vendor_external` · `varchar` · opcional
- 🕒 shelf_reviewed
  > `member_counts_2.shelf_reviewed` · `timestamp` · opcional
- 🔗 device_id <!-- orbit link: "#device_items", status: "chave estrangeira" -->
  > `member_counts_2.device_id` · `int8` · obrigatória
  >
  > Aponta para [`device_items`](#device_items).
- ☑️ order_started
  > `member_counts_2.order_started` · `bool` · obrigatória
- 🔗 ebook_id <!-- orbit link: "#ebook_queues", status: "chave estrangeira" -->
  > `member_counts_2.ebook_id` · `int8` · obrigatória
  >
  > Aponta para [`ebook_queues`](#ebook_queues).
- 🔤 vendor_code
  > `member_counts_2.vendor_code` · `varchar` · obrigatória
- ☑️ audit_amount
  > `member_counts_2.audit_amount` · `bool` · opcional
- 🔗 staff_id <!-- orbit link: "#staff_logs", status: "chave estrangeira" -->
  > `member_counts_2.staff_id` · `int8` · obrigatória
  >
  > Aponta para [`staff_logs`](#staff_logs).
- 🔗 loan_id <!-- orbit link: "#loan_counts", status: "chave estrangeira" -->
  > `member_counts_2.loan_id` · `int8` · obrigatória
  >
  > Aponta para [`loan_counts`](#loan_counts).
- 🔢 order_created
  > `member_counts_2.order_created` · `int4` · obrigatória
- 🧩 device_amount
  > `member_counts_2.device_amount` · `jsonb` · obrigatória
- ☑️ order_kind_2
  > `member_counts_2.order_kind_2` · `bool` · obrigatória
- 🔗 seriess_id <!-- orbit link: "#seriess", status: "chave estrangeira" -->
  > `member_counts_2.seriess_id` · `int8` · obrigatória
  >
  > Aponta para [`seriess`](#seriess).
- 🔢 fee_count
  > `member_counts_2.fee_count` · `int4` · obrigatória
- 🧩 book_name
  > `member_counts_2.book_name` · `jsonb` · obrigatória
- 🕒 budget_format
  > `member_counts_2.budget_format` · `timestamp` · obrigatória
- 🔤 audit_title
  > `member_counts_2.audit_title` · `varchar` · opcional

### review_versions
<!-- orbit
id: "review_versions"
icon: "📗"
collapsed: true
-->
> `review_versions` · tabela de Multas e pagamentos
>
> Uma linha por resenha, com 8 colunas e cerca de 753 mil linhas.
>
> **Pertence a** [`audit_links_4`](#audit_links_4)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `review_versions.id` · `int8` · obrigatória
- ☑️ copy_started
  > `review_versions.copy_started` · `bool` · obrigatória
- 🧩 event_name
  > `review_versions.event_name` · `jsonb` · obrigatória
- 🔗 license_id <!-- orbit link: "#license_logs_2", status: "chave estrangeira" -->
  > `review_versions.license_id` · `int8` · obrigatória
  >
  > Aponta para [`license_logs_2`](#license_logs_2).
- 🧩 order_priority
  > `review_versions.order_priority` · `jsonb` · opcional
- 🧩 event_language
  > `review_versions.event_language` · `jsonb` · obrigatória
- 🔗 report_id <!-- orbit link: "#report_links", status: "chave estrangeira" -->
  > `review_versions.report_id` · `int8` · obrigatória
  >
  > Aponta para [`report_links`](#report_links).
- 🔢 audit_language
  > `review_versions.audit_language` · `int4` · obrigatória

### author_links_3
<!-- orbit
id: "author_links_3"
icon: "📇"
collapsed: true
-->
> `author_links_3` · tabela de Multas e pagamentos
>
> Uma linha por autor, com 12 colunas e cerca de 688 mil linhas.
>
> **Pertence a** [`room_logs`](#room_logs) · [`report_logs_2`](#report_logs_2)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `author_links_3.id` · `int8` · obrigatória
- 🕒 budget_started
  > `author_links_3.budget_started` · `timestamp` · opcional
- 🔤 license_count
  > `author_links_3.license_count` · `varchar` · obrigatória
- 🔗 visit_id <!-- orbit link: "#visit_items", status: "chave estrangeira" -->
  > `author_links_3.visit_id` · `int8` · obrigatória
  >
  > Aponta para [`visit_items`](#visit_items).
- 🧩 branch_total
  > `author_links_3.branch_total` · `jsonb` · obrigatória
- ☑️ vendor_archived
  > `author_links_3.vendor_archived` · `bool` · opcional
- 🧩 branch_region
  > `author_links_3.branch_region` · `jsonb` · opcional
- 🔤 series_amount
  > `author_links_3.series_amount` · `varchar` · obrigatória
- 🧩 series_created
  > `author_links_3.series_created` · `jsonb` · opcional
- 🔗 donation_id <!-- orbit link: "#donation_history", status: "chave estrangeira" -->
  > `author_links_3.donation_id` · `int8` · obrigatória
  >
  > Aponta para [`donation_history`](#donation_history).
- ☑️ shelf_region
  > `author_links_3.shelf_region` · `bool` · obrigatória
- 🕒 fee_label
  > `author_links_3.fee_label` · `timestamp` · obrigatória

### device_settings
<!-- orbit
id: "device_settings"
icon: "📗"
collapsed: true
-->
> `device_settings` · tabela de Multas e pagamentos
>
> Uma linha por dispositivo, com 19 colunas e cerca de 218 mil linhas.
>
> **Pertence a** [`ebook_queues`](#ebook_queues) · [`license_settings`](#license_settings) · [`author_links_3`](#author_links_3)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `device_settings.id` · `int8` · obrigatória
- 🔤 repair_notes
  > `device_settings.repair_notes` · `varchar` · obrigatória
- 🔢 tag_started
  > `device_settings.tag_started` · `int4` · opcional
- ☑️ role_external
  > `device_settings.role_external` · `bool` · opcional
- 🧩 branch_reviewed
  > `device_settings.branch_reviewed` · `jsonb` · opcional
- 🔢 loan_state
  > `device_settings.loan_state` · `int4` · obrigatória
- 🔢 tag_updated
  > `device_settings.tag_updated` · `int4` · obrigatória
- 🔤 branch_source
  > `device_settings.branch_source` · `varchar` · opcional
- 🔢 visit_kind
  > `device_settings.visit_kind` · `int4` · opcional
- 🔤 shelf_started
  > `device_settings.shelf_started` · `varchar` · opcional
- 🔤 payment_reviewed
  > `device_settings.payment_reviewed` · `varchar` · opcional
- 🔢 device_external
  > `device_settings.device_external` · `int4` · obrigatória
- 🧩 event_priority
  > `device_settings.event_priority` · `jsonb` · obrigatória
- 🧩 role_position
  > `device_settings.role_position` · `jsonb` · obrigatória
- 🔤 book_created
  > `device_settings.book_created` · `varchar` · opcional
- 🔗 payment_id <!-- orbit link: "#payment_requests", status: "chave estrangeira" -->
  > `device_settings.payment_id` · `int8` · obrigatória
  >
  > Aponta para [`payment_requests`](#payment_requests).
- 🧩 branch_count
  > `device_settings.branch_count` · `jsonb` · obrigatória
- 🔢 author_state
  > `device_settings.author_state` · `int4` · obrigatória
- 🔗 book_id <!-- orbit link: "#book_items", status: "chave estrangeira" -->
  > `device_settings.book_id` · `int8` · obrigatória
  >
  > Aponta para [`book_items`](#book_items).

### staff_logs
<!-- orbit
id: "staff_logs"
icon: "🧮"
collapsed: true
-->
> `staff_logs` · tabela de Multas e pagamentos
>
> Uma linha por funcionário, com 21 colunas e cerca de 318 mil linhas.
>
> **Pertence a** [`device_items`](#device_items) · [`author_history`](#author_history) · [`report_logs`](#report_logs) · [`member_counts`](#member_counts)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `staff_logs.id` · `int8` · obrigatória
- ☑️ shelf_reviewed
  > `staff_logs.shelf_reviewed` · `bool` · obrigatória
- 🔤 member_count
  > `staff_logs.member_count` · `varchar` · opcional
- 🔗 staff_id <!-- orbit link: "#staff_counts", status: "chave estrangeira" -->
  > `staff_logs.staff_id` · `int8` · obrigatória
  >
  > Aponta para [`staff_counts`](#staff_counts).
- 🧩 device_language
  > `staff_logs.device_language` · `jsonb` · obrigatória
- 🕒 ebook_amount
  > `staff_logs.ebook_amount` · `timestamp` · obrigatória
- 🔢 license_name
  > `staff_logs.license_name` · `int4` · obrigatória
- 🔗 vendor_id <!-- orbit link: "#vendor_counts", status: "chave estrangeira" -->
  > `staff_logs.vendor_id` · `int8` · obrigatória
  >
  > Aponta para [`vendor_counts`](#vendor_counts).
- 🔤 role_public
  > `staff_logs.role_public` · `varchar` · obrigatória
- 🔗 loan_id <!-- orbit link: "#loan_logs_2", status: "chave estrangeira" -->
  > `staff_logs.loan_id` · `int8` · obrigatória
  >
  > Aponta para [`loan_logs_2`](#loan_logs_2).
- ☑️ branch_kind
  > `staff_logs.branch_kind` · `bool` · obrigatória
- 🔢 license_format
  > `staff_logs.license_format` · `int4` · opcional
- 🔢 budget_title
  > `staff_logs.budget_title` · `int4` · opcional
- 🔗 event_id <!-- orbit link: "#event_queues", status: "chave estrangeira" -->
  > `staff_logs.event_id` · `int8` · obrigatória
  >
  > Aponta para [`event_queues`](#event_queues).
- ☑️ loan_started
  > `staff_logs.loan_started` · `bool` · obrigatória
- ☑️ donation_active
  > `staff_logs.donation_active` · `bool` · opcional
- ☑️ notice_label
  > `staff_logs.notice_label` · `bool` · opcional
- 🔗 series_id <!-- orbit link: "#series_counts_2", status: "chave estrangeira" -->
  > `staff_logs.series_id` · `int8` · obrigatória
  >
  > Aponta para [`series_counts_2`](#series_counts_2).
- 🕒 vendor_started
  > `staff_logs.vendor_started` · `timestamp` · opcional
- 🔗 tag_id <!-- orbit link: "#tag_settings", status: "chave estrangeira" -->
  > `staff_logs.tag_id` · `int8` · obrigatória
  >
  > Aponta para [`tag_settings`](#tag_settings).
- 🔤 vendor_active
  > `staff_logs.vendor_active` · `varchar` · obrigatória

### loan_counts
<!-- orbit
id: "loan_counts"
icon: "📗"
collapsed: true
-->
> `loan_counts` · tabela de Multas e pagamentos
>
> Uma linha por empréstimo, com 13 colunas e cerca de 672 mil linhas.
>
> **Pertence a** [`role_counts`](#role_counts) · [`license_queues`](#license_queues) · [`device_settings`](#device_settings)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `loan_counts.id` · `int8` · obrigatória
- 🔗 notice_id <!-- orbit link: "#notice_counts", status: "chave estrangeira" -->
  > `loan_counts.notice_id` · `int8` · obrigatória
  >
  > Aponta para [`notice_counts`](#notice_counts).
- 🧩 series_code
  > `loan_counts.series_code` · `jsonb` · opcional
- ☑️ role_ended
  > `loan_counts.role_ended` · `bool` · obrigatória
- 🔤 shelf_updated
  > `loan_counts.shelf_updated` · `varchar` · obrigatória
- 🔤 branch_public
  > `loan_counts.branch_public` · `varchar` · obrigatória
- 🔢 fee_priority
  > `loan_counts.fee_priority` · `int4` · opcional
- 🕒 event_external
  > `loan_counts.event_external` · `timestamp` · opcional
- ☑️ room_code
  > `loan_counts.room_code` · `bool` · obrigatória
- 🔤 license_position
  > `loan_counts.license_position` · `varchar` · opcional
- 🕒 staff_label
  > `loan_counts.staff_label` · `timestamp` · obrigatória
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
> `report_queues_2` · tabela de Multas e pagamentos
>
> Uma linha por relatório, com 24 colunas e cerca de 666 mil linhas.
>
> **Pertence a** [`vendor_counts`](#vendor_counts) · [`copy_versions`](#copy_versions) · [`ebook_logs`](#ebook_logs)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `report_queues_2.id` · `int8` · obrigatória
- ☑️ repair_archived
  > `report_queues_2.repair_archived` · `bool` · opcional
- 🧩 donation_amount
  > `report_queues_2.donation_amount` · `jsonb` · opcional
- 🕒 hold_source
  > `report_queues_2.hold_source` · `timestamp` · obrigatória
- 🔗 tags_id <!-- orbit link: "#tags", status: "chave estrangeira" -->
  > `report_queues_2.tags_id` · `int8` · obrigatória
  >
  > Aponta para [`tags`](#tags).
- 🔗 vendor_id <!-- orbit link: "#vendor_history", status: "chave estrangeira" -->
  > `report_queues_2.vendor_id` · `int8` · obrigatória
  >
  > Aponta para [`vendor_history`](#vendor_history).
- 🔤 repair_state
  > `report_queues_2.repair_state` · `varchar` · opcional
- 🧩 payment_ended
  > `report_queues_2.payment_ended` · `jsonb` · obrigatória
- 🕒 repair_kind
  > `report_queues_2.repair_kind` · `timestamp` · obrigatória
- 🕒 member_status
  > `report_queues_2.member_status` · `timestamp` · obrigatória
- 🔢 repair_name
  > `report_queues_2.repair_name` · `int4` · opcional
- 🔗 series_id <!-- orbit link: "#series_requests", status: "chave estrangeira" -->
  > `report_queues_2.series_id` · `int8` · obrigatória
  >
  > Aponta para [`series_requests`](#series_requests).
- 🔗 audit_id <!-- orbit link: "#audit_links_4", status: "chave estrangeira" -->
  > `report_queues_2.audit_id` · `int8` · obrigatória
  >
  > Aponta para [`audit_links_4`](#audit_links_4).
- 🔢 order_language
  > `report_queues_2.order_language` · `int4` · opcional
- ☑️ shelf_kind
  > `report_queues_2.shelf_kind` · `bool` · opcional
- ☑️ ebook_language
  > `report_queues_2.ebook_language` · `bool` · opcional
- 🔗 budget_id <!-- orbit link: "#budget_queues", status: "chave estrangeira" -->
  > `report_queues_2.budget_id` · `int8` · obrigatória
  >
  > Aponta para [`budget_queues`](#budget_queues).
- 🕒 order_active
  > `report_queues_2.order_active` · `timestamp` · opcional
- ☑️ shelf_total
  > `report_queues_2.shelf_total` · `bool` · obrigatória
- 🧩 device_state
  > `report_queues_2.device_state` · `jsonb` · obrigatória
- 🔤 budget_public
  > `report_queues_2.budget_public` · `varchar` · obrigatória
- 🔗 copy_id <!-- orbit link: "#copy_links", status: "chave estrangeira" -->
  > `report_queues_2.copy_id` · `int8` · obrigatória
  >
  > Aponta para [`copy_links`](#copy_links).
- 🔗 hold_id <!-- orbit link: "#hold_links_2", status: "chave estrangeira" -->
  > `report_queues_2.hold_id` · `int8` · obrigatória
  >
  > Aponta para [`hold_links_2`](#hold_links_2).
- ☑️ budget_created
  > `report_queues_2.budget_created` · `bool` · obrigatória

### card_logs
<!-- orbit
id: "card_logs"
icon: "🗃️"
collapsed: true
-->
> `card_logs` · tabela de Multas e pagamentos
>
> Uma linha por carteirinha, com 15 colunas e cerca de 177 mil linhas.
>
> **Pertence a** [`vendor_history`](#vendor_history) · [`budgets`](#budgets) · [`fee_requests`](#fee_requests) · [`shelf_queues`](#shelf_queues)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `card_logs.id` · `int8` · obrigatória
- 🧩 room_priority
  > `card_logs.room_priority` · `jsonb` · opcional
- 🔗 review_id <!-- orbit link: "#review_logs", status: "chave estrangeira" -->
  > `card_logs.review_id` · `int8` · obrigatória
  >
  > Aponta para [`review_logs`](#review_logs).
- ☑️ report_total
  > `card_logs.report_total` · `bool` · obrigatória
- 🔗 audit_id <!-- orbit link: "#audit_settings", status: "chave estrangeira" -->
  > `card_logs.audit_id` · `int8` · obrigatória
  >
  > Aponta para [`audit_settings`](#audit_settings).
- 🕒 room_status
  > `card_logs.room_status` · `timestamp` · opcional
- 🧩 repair_updated
  > `card_logs.repair_updated` · `jsonb` · obrigatória
- ☑️ repair_count
  > `card_logs.repair_count` · `bool` · obrigatória
- 🧩 review_source
  > `card_logs.review_source` · `jsonb` · obrigatória
- 🔤 ebook_active
  > `card_logs.ebook_active` · `varchar` · opcional
- ☑️ tag_ended
  > `card_logs.tag_ended` · `bool` · obrigatória
- ☑️ notice_public
  > `card_logs.notice_public` · `bool` · obrigatória
- 🔗 ebook_id <!-- orbit link: "#ebook_versions", status: "chave estrangeira" -->
  > `card_logs.ebook_id` · `int8` · obrigatória
  >
  > Aponta para [`ebook_versions`](#ebook_versions).
- ☑️ fee_amount
  > `card_logs.fee_amount` · `bool` · opcional
- 🔤 copy_region
  > `card_logs.copy_region` · `varchar` · obrigatória

### donation_queues
<!-- orbit
id: "donation_queues"
icon: "🧮"
collapsed: true
-->
> `donation_queues` · tabela de Multas e pagamentos
>
> Uma linha por doação, com 15 colunas e cerca de 114 mil linhas.
>
> **Pertence a** [`role_counts`](#role_counts) · [`author_history`](#author_history)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `donation_queues.id` · `int8` · obrigatória
- 🔢 budget_count
  > `donation_queues.budget_count` · `int4` · opcional
- 🕒 license_notes
  > `donation_queues.license_notes` · `timestamp` · opcional
- 🔢 staff_public
  > `donation_queues.staff_public` · `int4` · opcional
- 🔤 book_kind
  > `donation_queues.book_kind` · `varchar` · opcional
- ☑️ room_count
  > `donation_queues.room_count` · `bool` · obrigatória
- 🔢 branch_active
  > `donation_queues.branch_active` · `int4` · opcional
- 🧩 visit_ended
  > `donation_queues.visit_ended` · `jsonb` · obrigatória
- 🕒 loan_state
  > `donation_queues.loan_state` · `timestamp` · opcional
- 🔗 holds_id <!-- orbit link: "#holds", status: "chave estrangeira" -->
  > `donation_queues.holds_id` · `int8` · obrigatória
  >
  > Aponta para [`holds`](#holds).
- 🔢 donation_source
  > `donation_queues.donation_source` · `int4` · obrigatória
- ☑️ donation_label
  > `donation_queues.donation_label` · `bool` · opcional
- 🔢 ebook_priority
  > `donation_queues.ebook_priority` · `int4` · opcional
- ☑️ donation_title
  > `donation_queues.donation_title` · `bool` · obrigatória
- 🕒 report_amount
  > `donation_queues.report_amount` · `timestamp` · opcional

### author_history
<!-- orbit
id: "author_history"
icon: "🧷"
collapsed: true
-->
> `author_history` · tabela de Multas e pagamentos
>
> Uma linha por autor, com 23 colunas e cerca de 380 mil linhas.
>
> **Pertence a** [`vendor_history`](#vendor_history)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `author_history.id` · `int8` · obrigatória
- ☑️ notice_language
  > `author_history.notice_language` · `bool` · obrigatória
- 🕒 budget_source
  > `author_history.budget_source` · `timestamp` · obrigatória
- 🕒 event_ended
  > `author_history.event_ended` · `timestamp` · obrigatória
- 🔗 events_id <!-- orbit link: "#events", status: "chave estrangeira" -->
  > `author_history.events_id` · `int8` · obrigatória
  >
  > Aponta para [`events`](#events).
- 🕒 room_position
  > `author_history.room_position` · `timestamp` · obrigatória
- ☑️ order_total
  > `author_history.order_total` · `bool` · obrigatória
- 🔗 budget_id <!-- orbit link: "#budget_settings_2", status: "chave estrangeira" -->
  > `author_history.budget_id` · `int8` · obrigatória
  >
  > Aponta para [`budget_settings_2`](#budget_settings_2).
- 🧩 staff_external
  > `author_history.staff_external` · `jsonb` · obrigatória
- 🔗 author_id <!-- orbit link: "#author_links_2", status: "chave estrangeira" -->
  > `author_history.author_id` · `int8` · obrigatória
  >
  > Aponta para [`author_links_2`](#author_links_2).
- 🔗 book_id <!-- orbit link: "#book_items", status: "chave estrangeira" -->
  > `author_history.book_id` · `int8` · obrigatória
  >
  > Aponta para [`book_items`](#book_items).
- 🕒 payment_source
  > `author_history.payment_source` · `timestamp` · opcional
- 🧩 order_name
  > `author_history.order_name` · `jsonb` · obrigatória
- 🔢 ebook_active
  > `author_history.ebook_active` · `int4` · obrigatória
- 🧩 card_source
  > `author_history.card_source` · `jsonb` · opcional
- 🔤 vendor_notes
  > `author_history.vendor_notes` · `varchar` · obrigatória
- 🕒 loan_reviewed
  > `author_history.loan_reviewed` · `timestamp` · opcional
- 🔗 branch_id <!-- orbit link: "#branch_links", status: "chave estrangeira" -->
  > `author_history.branch_id` · `int8` · obrigatória
  >
  > Aponta para [`branch_links`](#branch_links).
- 🔗 tag_id <!-- orbit link: "#tag_settings", status: "chave estrangeira" -->
  > `author_history.tag_id` · `int8` · obrigatória
  >
  > Aponta para [`tag_settings`](#tag_settings).
- 🕒 visit_reviewed
  > `author_history.visit_reviewed` · `timestamp` · opcional
- 🧩 payment_format
  > `author_history.payment_format` · `jsonb` · opcional
- 🧩 hold_position
  > `author_history.hold_position` · `jsonb` · obrigatória
- 🔗 review_id <!-- orbit link: "#review_queues", status: "chave estrangeira" -->
  > `author_history.review_id` · `int8` · obrigatória
  >
  > Aponta para [`review_queues`](#review_queues).

### repair_requests
<!-- orbit
id: "repair_requests"
icon: "📇"
collapsed: true
-->
> `repair_requests` · tabela de Multas e pagamentos
>
> Uma linha por reparo, com 23 colunas e cerca de 93 mil linhas.
>
> **Pertence a** [`copy_links`](#copy_links) · [`notice_counts`](#notice_counts) · [`license_versions`](#license_versions) · [`role_counts`](#role_counts)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `repair_requests.id` · `int8` · obrigatória
- ☑️ donation_label
  > `repair_requests.donation_label` · `bool` · opcional
- 🔗 role_id <!-- orbit link: "#role_counts", status: "chave estrangeira" -->
  > `repair_requests.role_id` · `int8` · obrigatória
  >
  > Aponta para [`role_counts`](#role_counts).
- 🕒 book_archived
  > `repair_requests.book_archived` · `timestamp` · opcional
- 🔤 tag_source
  > `repair_requests.tag_source` · `varchar` · obrigatória
- 🧩 series_priority
  > `repair_requests.series_priority` · `jsonb` · obrigatória
- 🕒 report_position
  > `repair_requests.report_position` · `timestamp` · opcional
- 🔢 audit_reviewed
  > `repair_requests.audit_reviewed` · `int4` · obrigatória
- ☑️ role_reviewed
  > `repair_requests.role_reviewed` · `bool` · obrigatória
- ☑️ review_amount
  > `repair_requests.review_amount` · `bool` · opcional
- 🕒 staff_position
  > `repair_requests.staff_position` · `timestamp` · obrigatória
- 🔗 repair_id <!-- orbit link: "#repair_requests", status: "chave estrangeira" -->
  > `repair_requests.repair_id` · `int8` · obrigatória
  >
  > Aponta para [`repair_requests`](#repair_requests).
- 🧩 book_code
  > `repair_requests.book_code` · `jsonb` · opcional
- 🕒 donation_reviewed
  > `repair_requests.donation_reviewed` · `timestamp` · opcional
- ☑️ visit_label
  > `repair_requests.visit_label` · `bool` · obrigatória
- 🔢 donation_created
  > `repair_requests.donation_created` · `int4` · obrigatória
- 🔤 device_language
  > `repair_requests.device_language` · `varchar` · obrigatória
- 🕒 shelf_amount
  > `repair_requests.shelf_amount` · `timestamp` · obrigatória
- 🔗 shelf_id <!-- orbit link: "#shelf_counts", status: "chave estrangeira" -->
  > `repair_requests.shelf_id` · `int8` · obrigatória
  >
  > Aponta para [`shelf_counts`](#shelf_counts).
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
> `tags` · tabela de Multas e pagamentos
>
> Uma linha por etiqueta, com 7 colunas e cerca de 114 mil linhas.
>
> **Pertence a** [`hold_links_2`](#hold_links_2)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `tags.id` · `int8` · obrigatória
- ☑️ fee_created
  > `tags.fee_created` · `bool` · obrigatória
- 🕒 loan_archived
  > `tags.loan_archived` · `timestamp` · opcional
- ☑️ review_language
  > `tags.review_language` · `bool` · obrigatória
- 🔢 tag_status
  > `tags.tag_status` · `int4` · opcional
- 🔤 budget_amount
  > `tags.budget_amount` · `varchar` · opcional
- ☑️ copy_total
  > `tags.copy_total` · `bool` · obrigatória

## 📦 Aquisições
<!-- orbit
id: "area-acquisitions"
color: "#0EA5E9"
-->
> 9 tabelas. As maiores: [`member_versions`](#member_versions), [`shelf_counts`](#shelf_counts), [`payment_versions`](#payment_versions).
>
> **Relacionada a** [`donation_requests`](#donation_requests) · [`ebook_versions`](#ebook_versions) · [`copy_history`](#copy_history)

### member_versions
<!-- orbit
id: "member_versions"
icon: "📇"
collapsed: true
-->
> `member_versions` · tabela de Aquisições
>
> Uma linha por leitor, com 9 colunas e cerca de 355 mil linhas.
>
> **Pertence a** [`fee_requests`](#fee_requests) · [`series_counts`](#series_counts)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `member_versions.id` · `int8` · obrigatória
- 🔢 vendor_name
  > `member_versions.vendor_name` · `int4` · obrigatória
- 🔢 visit_notes
  > `member_versions.visit_notes` · `int4` · obrigatória
- ☑️ event_position
  > `member_versions.event_position` · `bool` · obrigatória
- 🔗 member_id <!-- orbit link: "#member_counts_2", status: "chave estrangeira" -->
  > `member_versions.member_id` · `int8` · obrigatória
  >
  > Aponta para [`member_counts_2`](#member_counts_2).
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
> `shelf_counts` · tabela de Aquisições
>
> Uma linha por estante, com 19 colunas e cerca de 124 mil linhas.
>
> **Pertence a** [`budget_settings_2`](#budget_settings_2) · [`license_queues_2`](#license_queues_2)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `shelf_counts.id` · `int8` · obrigatória
- 🔤 device_position
  > `shelf_counts.device_position` · `varchar` · opcional
- ☑️ fee_status
  > `shelf_counts.fee_status` · `bool` · opcional
- 🔢 role_label
  > `shelf_counts.role_label` · `int4` · obrigatória
- 🔢 staff_total
  > `shelf_counts.staff_total` · `int4` · opcional
- 🧩 loan_source
  > `shelf_counts.loan_source` · `jsonb` · obrigatória
- 🔢 series_region
  > `shelf_counts.series_region` · `int4` · obrigatória
- 🔢 fee_format
  > `shelf_counts.fee_format` · `int4` · obrigatória
- 🧩 ebook_source
  > `shelf_counts.ebook_source` · `jsonb` · opcional
- 🕒 room_ended
  > `shelf_counts.room_ended` · `timestamp` · opcional
- ☑️ room_reviewed
  > `shelf_counts.room_reviewed` · `bool` · opcional
- ☑️ shelf_position
  > `shelf_counts.shelf_position` · `bool` · obrigatória
- 🔤 event_total
  > `shelf_counts.event_total` · `varchar` · obrigatória
- 🕒 branch_name
  > `shelf_counts.branch_name` · `timestamp` · obrigatória
- 🔗 report_id <!-- orbit link: "#report_links", status: "chave estrangeira" -->
  > `shelf_counts.report_id` · `int8` · obrigatória
  >
  > Aponta para [`report_links`](#report_links).
- 🕒 notice_started
  > `shelf_counts.notice_started` · `timestamp` · obrigatória
- 🕒 license_amount
  > `shelf_counts.license_amount` · `timestamp` · opcional
- 🔗 license_id <!-- orbit link: "#license_queues_2", status: "chave estrangeira" -->
  > `shelf_counts.license_id` · `int8` · obrigatória
  >
  > Aponta para [`license_queues_2`](#license_queues_2).
- 🔢 loan_language
  > `shelf_counts.loan_language` · `int4` · obrigatória

### payment_versions
<!-- orbit
id: "payment_versions"
icon: "🧷"
collapsed: true
-->
> `payment_versions` · tabela de Aquisições
>
> Uma linha por pagamento, com 5 colunas e cerca de 504 mil linhas.
>
> **Pertence a** [`hold_logs`](#hold_logs) · [`room_counts`](#room_counts)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `payment_versions.id` · `int8` · obrigatória
- 🔗 copy_id <!-- orbit link: "#copy_history", status: "chave estrangeira" -->
  > `payment_versions.copy_id` · `int8` · obrigatória
  >
  > Aponta para [`copy_history`](#copy_history).
- 🧩 report_amount
  > `payment_versions.report_amount` · `jsonb` · opcional
- 🧩 card_label
  > `payment_versions.card_label` · `jsonb` · obrigatória
- 🧩 author_started
  > `payment_versions.author_started` · `jsonb` · opcional

### room_counts
<!-- orbit
id: "room_counts"
icon: "🧮"
collapsed: true
-->
> `room_counts` · tabela de Aquisições
>
> Uma linha por sala, com 18 colunas e cerca de 653 mil linhas.
>
> **Pertence a** [`audit_links_4`](#audit_links_4) · [`payment_versions`](#payment_versions) · [`license_logs`](#license_logs) · [`room_logs`](#room_logs)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `room_counts.id` · `int8` · obrigatória
- 🔢 license_label
  > `room_counts.license_label` · `int4` · opcional
- 🧩 payment_total
  > `room_counts.payment_total` · `jsonb` · obrigatória
- 🔤 vendor_public
  > `room_counts.vendor_public` · `varchar` · obrigatória
- ☑️ tag_archived
  > `room_counts.tag_archived` · `bool` · opcional
- 🧩 card_source
  > `room_counts.card_source` · `jsonb` · opcional
- 🔢 hold_priority
  > `room_counts.hold_priority` · `int4` · opcional
- ☑️ report_name
  > `room_counts.report_name` · `bool` · opcional
- 🔢 license_notes
  > `room_counts.license_notes` · `int4` · obrigatória
- ☑️ visit_state
  > `room_counts.visit_state` · `bool` · opcional
- 🔗 audit_id <!-- orbit link: "#audit_links_3", status: "chave estrangeira" -->
  > `room_counts.audit_id` · `int8` · obrigatória
  >
  > Aponta para [`audit_links_3`](#audit_links_3).
- 🔗 events_id <!-- orbit link: "#events", status: "chave estrangeira" -->
  > `room_counts.events_id` · `int8` · obrigatória
  >
  > Aponta para [`events`](#events).
- 🧩 staff_total
  > `room_counts.staff_total` · `jsonb` · opcional
- ☑️ payment_status
  > `room_counts.payment_status` · `bool` · opcional
- 🧩 author_label
  > `room_counts.author_label` · `jsonb` · opcional
- 🔗 book_id <!-- orbit link: "#book_links", status: "chave estrangeira" -->
  > `room_counts.book_id` · `int8` · obrigatória
  >
  > Aponta para [`book_links`](#book_links).
- 🔗 repair_id <!-- orbit link: "#repair_requests", status: "chave estrangeira" -->
  > `room_counts.repair_id` · `int8` · obrigatória
  >
  > Aponta para [`repair_requests`](#repair_requests).
- ☑️ hold_state
  > `room_counts.hold_state` · `bool` · opcional

### order_items
<!-- orbit
id: "order_items"
icon: "🧮"
collapsed: true
-->
> `order_items` · tabela de Aquisições
>
> Uma linha por pedido, com 14 colunas e cerca de 879 mil linhas.
>
> **Pertence a** [`license_logs_2`](#license_logs_2) · [`budgets`](#budgets) · [`report_queues`](#report_queues) · [`tag_requests`](#tag_requests)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `order_items.id` · `int8` · obrigatória
- 🔗 rooms_id <!-- orbit link: "#rooms", status: "chave estrangeira" -->
  > `order_items.rooms_id` · `int8` · obrigatória
  >
  > Aponta para [`rooms`](#rooms).
- 🔢 donation_title
  > `order_items.donation_title` · `int4` · obrigatória
- ☑️ review_language
  > `order_items.review_language` · `bool` · obrigatória
- ☑️ visit_region
  > `order_items.visit_region` · `bool` · opcional
- 🧩 copy_updated
  > `order_items.copy_updated` · `jsonb` · obrigatória
- 🔤 budget_archived
  > `order_items.budget_archived` · `varchar` · opcional
- 🔢 license_code
  > `order_items.license_code` · `int4` · obrigatória
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
  > `order_items.report_code` · `int4` · obrigatória

### report_items
<!-- orbit
id: "report_items"
icon: "🧮"
collapsed: true
-->
> `report_items` · tabela de Aquisições
>
> Uma linha por relatório, com 20 colunas e cerca de 41 mil linhas.
>
> **Pertence a** [`device_items`](#device_items) · [`fee_requests`](#fee_requests) · [`visit_queues`](#visit_queues)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `report_items.id` · `int8` · obrigatória
- 🧩 report_created
  > `report_items.report_created` · `jsonb` · obrigatória
- 🧩 license_source
  > `report_items.license_source` · `jsonb` · opcional
- 🔢 repair_source
  > `report_items.repair_source` · `int4` · opcional
- 🧩 event_ended
  > `report_items.event_ended` · `jsonb` · obrigatória
- 🔢 loan_title
  > `report_items.loan_title` · `int4` · opcional
- 🔗 donation_id <!-- orbit link: "#donation_requests", status: "chave estrangeira" -->
  > `report_items.donation_id` · `int8` · obrigatória
  >
  > Aponta para [`donation_requests`](#donation_requests).
- 🔤 ebook_amount
  > `report_items.ebook_amount` · `varchar` · opcional
- 🔢 shelf_archived
  > `report_items.shelf_archived` · `int4` · opcional
- 🧩 order_created
  > `report_items.order_created` · `jsonb` · obrigatória
- 🧩 device_code
  > `report_items.device_code` · `jsonb` · opcional
- 🧩 visit_state
  > `report_items.visit_state` · `jsonb` · obrigatória
- 🕒 branch_code
  > `report_items.branch_code` · `timestamp` · opcional
- 🧩 copy_public
  > `report_items.copy_public` · `jsonb` · opcional
- 🔢 shelf_public
  > `report_items.shelf_public` · `int4` · opcional
- 🧩 ebook_label
  > `report_items.ebook_label` · `jsonb` · opcional
- 🧩 author_ended
  > `report_items.author_ended` · `jsonb` · obrigatória
- 🧩 ebook_name
  > `report_items.ebook_name` · `jsonb` · obrigatória
- 🔤 budget_public
  > `report_items.budget_public` · `varchar` · obrigatória
- 🧩 loan_kind
  > `report_items.loan_kind` · `jsonb` · obrigatória

### ebook_queues
<!-- orbit
id: "ebook_queues"
icon: "📇"
collapsed: true
-->
> `ebook_queues` · tabela de Aquisições
>
> Uma linha por livro digital, com 4 colunas e cerca de 346 mil linhas.
>
> **Pertence a** [`book_links`](#book_links) · [`license_logs`](#license_logs) · [`card_links_2`](#card_links_2) · [`donation_links`](#donation_links)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `ebook_queues.id` · `int8` · obrigatória
- 🕒 license_position
  > `ebook_queues.license_position` · `timestamp` · obrigatória
- 🕒 event_count
  > `ebook_queues.event_count` · `timestamp` · obrigatória
- 🕒 fee_format
  > `ebook_queues.fee_format` · `timestamp` · obrigatória

### license_queues
<!-- orbit
id: "license_queues"
icon: "🧷"
collapsed: true
-->
> `license_queues` · tabela de Aquisições
>
> Uma linha por licença, com 9 colunas e cerca de 480 mil linhas.
>
> **Pertence a** [`review_queues`](#review_queues) · [`ebook_versions`](#ebook_versions) · [`fee_requests`](#fee_requests) · [`series_requests`](#series_requests)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `license_queues.id` · `int8` · obrigatória
- 🕒 payment_priority
  > `license_queues.payment_priority` · `timestamp` · obrigatória
- ☑️ donation_reviewed
  > `license_queues.donation_reviewed` · `bool` · obrigatória
- ☑️ fee_total
  > `license_queues.fee_total` · `bool` · obrigatória
- 🧩 book_amount
  > `license_queues.book_amount` · `jsonb` · obrigatória
- 🔗 tag_id <!-- orbit link: "#tag_requests", status: "chave estrangeira" -->
  > `license_queues.tag_id` · `int8` · obrigatória
  >
  > Aponta para [`tag_requests`](#tag_requests).
- 🔢 series_notes
  > `license_queues.series_notes` · `int4` · obrigatória
- ☑️ budget_code
  > `license_queues.budget_code` · `bool` · obrigatória
- 🔤 ebook_started
  > `license_queues.ebook_started` · `varchar` · obrigatória

### notice_requests
<!-- orbit
id: "notice_requests"
icon: "🧮"
collapsed: true
-->
> `notice_requests` · tabela de Aquisições
>
> Uma linha por aviso, com 5 colunas e cerca de 269 mil linhas.
>
> **Pertence a** [`report_logs`](#report_logs)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `notice_requests.id` · `int8` · obrigatória
- 🧩 series_created
  > `notice_requests.series_created` · `jsonb` · opcional
- 🕒 vendor_external
  > `notice_requests.vendor_external` · `timestamp` · obrigatória
- 🔗 role_id <!-- orbit link: "#role_counts", status: "chave estrangeira" -->
  > `notice_requests.role_id` · `int8` · obrigatória
  >
  > Aponta para [`role_counts`](#role_counts).
- 🔢 budget_state
  > `notice_requests.budget_state` · `int4` · opcional

## 🧾 Relatórios
<!-- orbit
id: "area-reports"
color: "#64748B"
-->
> 12 tabelas. As maiores: [`ebook_logs`](#ebook_logs), [`vendor_history`](#vendor_history), [`device_items`](#device_items).
>
> **Relacionada a** [`order_logs`](#order_logs) · [`report_logs_2`](#report_logs_2) · [`audit_settings`](#audit_settings)

### ebook_logs
<!-- orbit
id: "ebook_logs"
icon: "🧮"
collapsed: true
-->
> `ebook_logs` · tabela de Relatórios
>
> Uma linha por livro digital, com 70 colunas e cerca de 735 mil linhas.
>
> **Pertence a** [`audit_links_3`](#audit_links_3) · [`member_counts`](#member_counts) · [`audit_settings`](#audit_settings)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `ebook_logs.id` · `int8` · obrigatória
- 🧩 donation_updated
  > `ebook_logs.donation_updated` · `jsonb` · obrigatória
- 🔢 audit_format
  > `ebook_logs.audit_format` · `int4` · obrigatória
- 🔤 review_kind
  > `ebook_logs.review_kind` · `varchar` · obrigatória
- 🧩 donation_count
  > `ebook_logs.donation_count` · `jsonb` · obrigatória
- 🔢 shelf_total
  > `ebook_logs.shelf_total` · `int4` · opcional
- 🔗 visit_id <!-- orbit link: "#visit_items", status: "chave estrangeira" -->
  > `ebook_logs.visit_id` · `int8` · obrigatória
  >
  > Aponta para [`visit_items`](#visit_items).
- ☑️ role_ended
  > `ebook_logs.role_ended` · `bool` · opcional
- 🔤 audit_archived
  > `ebook_logs.audit_archived` · `varchar` · opcional
- 🔢 order_format
  > `ebook_logs.order_format` · `int4` · obrigatória
- 🧩 series_kind
  > `ebook_logs.series_kind` · `jsonb` · opcional
- 🔤 copy_position
  > `ebook_logs.copy_position` · `varchar` · opcional
- 🧩 author_code
  > `ebook_logs.author_code` · `jsonb` · obrigatória
- 🔗 budget_id <!-- orbit link: "#budget_settings", status: "chave estrangeira" -->
  > `ebook_logs.budget_id` · `int8` · obrigatória
  >
  > Aponta para [`budget_settings`](#budget_settings).
- 🧩 series_name
  > `ebook_logs.series_name` · `jsonb` · opcional
- ☑️ author_active
  > `ebook_logs.author_active` · `bool` · opcional
- ☑️ hold_title
  > `ebook_logs.hold_title` · `bool` · obrigatória
- ☑️ notice_notes
  > `ebook_logs.notice_notes` · `bool` · opcional
- 🔢 device_updated
  > `ebook_logs.device_updated` · `int4` · opcional
- 🔢 report_kind
  > `ebook_logs.report_kind` · `int4` · obrigatória
- ☑️ hold_external
  > `ebook_logs.hold_external` · `bool` · opcional
- 🕒 report_notes
  > `ebook_logs.report_notes` · `timestamp` · opcional
- 🕒 room_reviewed
  > `ebook_logs.room_reviewed` · `timestamp` · obrigatória
- ☑️ report_updated
  > `ebook_logs.report_updated` · `bool` · opcional
- 🔗 review_id <!-- orbit link: "#review_links", status: "chave estrangeira" -->
  > `ebook_logs.review_id` · `int8` · obrigatória
  >
  > Aponta para [`review_links`](#review_links).
- 🔗 ebook_id <!-- orbit link: "#ebook_requests", status: "chave estrangeira" -->
  > `ebook_logs.ebook_id` · `int8` · obrigatória
  >
  > Aponta para [`ebook_requests`](#ebook_requests).
- ☑️ author_external
  > `ebook_logs.author_external` · `bool` · obrigatória
- 🔤 book_label
  > `ebook_logs.book_label` · `varchar` · opcional
- 🔗 member_id <!-- orbit link: "#member_versions", status: "chave estrangeira" -->
  > `ebook_logs.member_id` · `int8` · obrigatória
  >
  > Aponta para [`member_versions`](#member_versions).
- 🔤 copy_status
  > `ebook_logs.copy_status` · `varchar` · opcional
- ☑️ copy_amount
  > `ebook_logs.copy_amount` · `bool` · obrigatória
- ☑️ book_reviewed
  > `ebook_logs.book_reviewed` · `bool` · obrigatória
- 🕒 license_priority
  > `ebook_logs.license_priority` · `timestamp` · obrigatória
- ☑️ card_count
  > `ebook_logs.card_count` · `bool` · obrigatória
- 🧩 report_notes_2
  > `ebook_logs.report_notes_2` · `jsonb` · obrigatória
- 🔢 notice_language
  > `ebook_logs.notice_language` · `int4` · opcional
- 🕒 branch_position
  > `ebook_logs.branch_position` · `timestamp` · opcional
- 🔗 role_id <!-- orbit link: "#role_counts", status: "chave estrangeira" -->
  > `ebook_logs.role_id` · `int8` · obrigatória
  >
  > Aponta para [`role_counts`](#role_counts).
- 🔤 license_language
  > `ebook_logs.license_language` · `varchar` · opcional
- 🧩 review_ended
  > `ebook_logs.review_ended` · `jsonb` · obrigatória
- 🔗 device_id <!-- orbit link: "#device_items", status: "chave estrangeira" -->
  > `ebook_logs.device_id` · `int8` · obrigatória
  >
  > Aponta para [`device_items`](#device_items).
- 🔗 card_id <!-- orbit link: "#card_queues", status: "chave estrangeira" -->
  > `ebook_logs.card_id` · `int8` · obrigatória
  >
  > Aponta para [`card_queues`](#card_queues).
- 🔤 payment_notes
  > `ebook_logs.payment_notes` · `varchar` · opcional
- 🔗 author_id <!-- orbit link: "#author_links_2", status: "chave estrangeira" -->
  > `ebook_logs.author_id` · `int8` · obrigatória
  >
  > Aponta para [`author_links_2`](#author_links_2).
- 🕒 staff_notes
  > `ebook_logs.staff_notes` · `timestamp` · obrigatória
- 🔗 repair_id <!-- orbit link: "#repair_requests", status: "chave estrangeira" -->
  > `ebook_logs.repair_id` · `int8` · obrigatória
  >
  > Aponta para [`repair_requests`](#repair_requests).
- 🔤 member_reviewed
  > `ebook_logs.member_reviewed` · `varchar` · obrigatória
- 🔗 book_id <!-- orbit link: "#book_settings", status: "chave estrangeira" -->
  > `ebook_logs.book_id` · `int8` · obrigatória
  >
  > Aponta para [`book_settings`](#book_settings).
- 🔢 audit_count
  > `ebook_logs.audit_count` · `int4` · opcional
- ☑️ card_active
  > `ebook_logs.card_active` · `bool` · obrigatória
- 🔗 budgets_id <!-- orbit link: "#budgets", status: "chave estrangeira" -->
  > `ebook_logs.budgets_id` · `int8` · obrigatória
  >
  > Aponta para [`budgets`](#budgets).
- 🔤 role_kind
  > `ebook_logs.role_kind` · `varchar` · opcional
- 🔤 report_name
  > `ebook_logs.report_name` · `varchar` · opcional
- ☑️ fee_label
  > `ebook_logs.fee_label` · `bool` · obrigatória
- 🕒 role_position
  > `ebook_logs.role_position` · `timestamp` · obrigatória
- 🔗 author_id_2 <!-- orbit link: "#author_history", status: "chave estrangeira" -->
  > `ebook_logs.author_id_2` · `int8` · obrigatória
  >
  > Aponta para [`author_history`](#author_history).
- 🔗 audit_id <!-- orbit link: "#audit_settings", status: "chave estrangeira" -->
  > `ebook_logs.audit_id` · `int8` · obrigatória
  >
  > Aponta para [`audit_settings`](#audit_settings).
- 🔢 event_state
  > `ebook_logs.event_state` · `int4` · obrigatória
- 🕒 vendor_total
  > `ebook_logs.vendor_total` · `timestamp` · opcional
- 🕒 vendor_format
  > `ebook_logs.vendor_format` · `timestamp` · obrigatória
- 🧩 hold_active
  > `ebook_logs.hold_active` · `jsonb` · obrigatória
- ☑️ budget_name
  > `ebook_logs.budget_name` · `bool` · opcional
- 🔢 device_format
  > `ebook_logs.device_format` · `int4` · obrigatória
- 🧩 vendor_region
  > `ebook_logs.vendor_region` · `jsonb` · obrigatória
- 🔤 device_external
  > `ebook_logs.device_external` · `varchar` · obrigatória
- 🔗 card_id_2 <!-- orbit link: "#card_queues", status: "chave estrangeira" -->
  > `ebook_logs.card_id_2` · `int8` · obrigatória
  >
  > Aponta para [`card_queues`](#card_queues).
- 🕒 visit_format
  > `ebook_logs.visit_format` · `timestamp` · obrigatória
- ☑️ room_updated
  > `ebook_logs.room_updated` · `bool` · obrigatória
- 🧩 room_title
  > `ebook_logs.room_title` · `jsonb` · obrigatória
- 🔗 order_id <!-- orbit link: "#order_links", status: "chave estrangeira" -->
  > `ebook_logs.order_id` · `int8` · obrigatória
  >
  > Aponta para [`order_links`](#order_links).

### vendor_history
<!-- orbit
id: "vendor_history"
icon: "🧷"
collapsed: true
-->
> `vendor_history` · tabela de Relatórios
>
> Uma linha por fornecedor, com 20 colunas e cerca de 744 mil linhas.
>
> **Pertence a** [`series_links`](#series_links) · [`card_queues`](#card_queues) · [`room_counts`](#room_counts)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `vendor_history.id` · `int8` · obrigatória
- 🔢 budget_code
  > `vendor_history.budget_code` · `int4` · obrigatória
- 🔤 license_title
  > `vendor_history.license_title` · `varchar` · opcional
- 🕒 role_public
  > `vendor_history.role_public` · `timestamp` · obrigatória
- ☑️ hold_created
  > `vendor_history.hold_created` · `bool` · obrigatória
- 🔢 loan_count
  > `vendor_history.loan_count` · `int4` · obrigatória
- 🔗 events_id <!-- orbit link: "#events", status: "chave estrangeira" -->
  > `vendor_history.events_id` · `int8` · obrigatória
  >
  > Aponta para [`events`](#events).
- 🔤 member_priority
  > `vendor_history.member_priority` · `varchar` · obrigatória
- ☑️ member_ended
  > `vendor_history.member_ended` · `bool` · obrigatória
- ☑️ vendor_total
  > `vendor_history.vendor_total` · `bool` · obrigatória
- ☑️ book_started
  > `vendor_history.book_started` · `bool` · obrigatória
- 🕒 book_title
  > `vendor_history.book_title` · `timestamp` · opcional
- 🔤 visit_format
  > `vendor_history.visit_format` · `varchar` · obrigatória
- 🔢 budget_started
  > `vendor_history.budget_started` · `int4` · obrigatória
- 🔢 order_updated
  > `vendor_history.order_updated` · `int4` · obrigatória
- 🔤 report_started
  > `vendor_history.report_started` · `varchar` · obrigatória
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
> `device_items` · tabela de Relatórios
>
> Uma linha por dispositivo, com 9 colunas e cerca de 649 mil linhas.
>
> **Pertence a** [`vendor_items`](#vendor_items) · [`notice_requests`](#notice_requests) · [`vendor_history`](#vendor_history) · [`order_logs`](#order_logs)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `device_items.id` · `int8` · obrigatória
- 🧩 room_active
  > `device_items.room_active` · `jsonb` · opcional
- 🧩 role_created
  > `device_items.role_created` · `jsonb` · obrigatória
- 🕒 report_ended
  > `device_items.report_ended` · `timestamp` · opcional
- 🔗 vendor_id <!-- orbit link: "#vendor_counts", status: "chave estrangeira" -->
  > `device_items.vendor_id` · `int8` · obrigatória
  >
  > Aponta para [`vendor_counts`](#vendor_counts).
- ☑️ report_language
  > `device_items.report_language` · `bool` · opcional
- 🔢 book_code
  > `device_items.book_code` · `int4` · obrigatória
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
> `visits` · tabela de Relatórios
>
> Uma linha por visita, com 4 colunas e cerca de 220 mil linhas.
>
> **Pertence a** [`author_links_3`](#author_links_3) · [`room_counts`](#room_counts) · [`audit_links`](#audit_links) · [`series_history`](#series_history)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `visits.id` · `int8` · obrigatória
- 🔢 donation_kind
  > `visits.donation_kind` · `int4` · obrigatória
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
> `donation_history` · tabela de Relatórios
>
> Uma linha por doação, com 16 colunas e cerca de 652 mil linhas.
>
> **Pertence a** [`report_links`](#report_links)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `donation_history.id` · `int8` · obrigatória
- 🔗 hold_id <!-- orbit link: "#hold_logs", status: "chave estrangeira" -->
  > `donation_history.hold_id` · `int8` · obrigatória
  >
  > Aponta para [`hold_logs`](#hold_logs).
- 🔢 repair_state
  > `donation_history.repair_state` · `int4` · opcional
- 🔤 audit_notes
  > `donation_history.audit_notes` · `varchar` · opcional
- ☑️ tag_name
  > `donation_history.tag_name` · `bool` · obrigatória
- 🔤 copy_title
  > `donation_history.copy_title` · `varchar` · opcional
- 🕒 book_code
  > `donation_history.book_code` · `timestamp` · obrigatória
- 🔤 audit_notes_2
  > `donation_history.audit_notes_2` · `varchar` · opcional
- 🕒 hold_started
  > `donation_history.hold_started` · `timestamp` · obrigatória
- 🧩 tag_status
  > `donation_history.tag_status` · `jsonb` · obrigatória
- 🔤 author_format
  > `donation_history.author_format` · `varchar` · obrigatória
- 🔤 review_state
  > `donation_history.review_state` · `varchar` · obrigatória
- 🔢 visit_kind
  > `donation_history.visit_kind` · `int4` · obrigatória
- 🕒 report_total
  > `donation_history.report_total` · `timestamp` · opcional
- 🔗 room_id <!-- orbit link: "#room_logs", status: "chave estrangeira" -->
  > `donation_history.room_id` · `int8` · obrigatória
  >
  > Aponta para [`room_logs`](#room_logs).
- 🧩 visit_count
  > `donation_history.visit_count` · `jsonb` · opcional

### hold_links_2
<!-- orbit
id: "hold_links_2"
icon: "🗃️"
collapsed: true
-->
> `hold_links_2` · tabela de Relatórios
>
> Uma linha por reserva, com 5 colunas e cerca de 648 mil linhas.
>
> **Pertence a** [`hold_links`](#hold_links)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `hold_links_2.id` · `int8` · obrigatória
- 🔤 audit_state
  > `hold_links_2.audit_state` · `varchar` · opcional
- 🔤 loan_source
  > `hold_links_2.loan_source` · `varchar` · obrigatória
- ☑️ fee_ended
  > `hold_links_2.fee_ended` · `bool` · obrigatória
- 🕒 book_ended
  > `hold_links_2.book_ended` · `timestamp` · obrigatória

### license_queues_2
<!-- orbit
id: "license_queues_2"
icon: "🧮"
collapsed: true
-->
> `license_queues_2` · tabela de Relatórios
>
> Uma linha por licença, com 5 colunas e cerca de 292 mil linhas.
>
> **Pertence a** [`member_history`](#member_history) · [`donation_history`](#donation_history)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `license_queues_2.id` · `int8` · obrigatória
- 🔢 audit_state
  > `license_queues_2.audit_state` · `int4` · obrigatória
- 🔢 device_active
  > `license_queues_2.device_active` · `int4` · obrigatória
- ☑️ copy_format
  > `license_queues_2.copy_format` · `bool` · obrigatória
- 🧩 series_public
  > `license_queues_2.series_public` · `jsonb` · obrigatória

### rooms
<!-- orbit
id: "rooms"
icon: "📇"
collapsed: true
-->
> `rooms` · tabela de Relatórios
>
> Uma linha por sala, com 23 colunas e cerca de 49 mil linhas.
>
> **Pertence a** [`visit_queues`](#visit_queues)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `rooms.id` · `int8` · obrigatória
- 🔤 donation_external
  > `rooms.donation_external` · `varchar` · opcional
- 🔤 role_started
  > `rooms.role_started` · `varchar` · obrigatória
- 🔤 visit_priority
  > `rooms.visit_priority` · `varchar` · obrigatória
- 🧩 report_format
  > `rooms.report_format` · `jsonb` · opcional
- 🔗 event_id <!-- orbit link: "#event_history", status: "chave estrangeira" -->
  > `rooms.event_id` · `int8` · obrigatória
  >
  > Aponta para [`event_history`](#event_history).
- 🔢 book_amount
  > `rooms.book_amount` · `int4` · obrigatória
- 🧩 role_label
  > `rooms.role_label` · `jsonb` · obrigatória
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
  > `rooms.loan_amount` · `bool` · obrigatória
- 🧩 book_position
  > `rooms.book_position` · `jsonb` · opcional
- 🔢 device_format
  > `rooms.device_format` · `int4` · opcional
- 🔢 author_public
  > `rooms.author_public` · `int4` · opcional
- 🔢 role_public
  > `rooms.role_public` · `int4` · obrigatória
- 🔢 role_position
  > `rooms.role_position` · `int4` · obrigatória
- 🕒 shelf_label
  > `rooms.shelf_label` · `timestamp` · obrigatória
- ☑️ budget_code
  > `rooms.budget_code` · `bool` · opcional

### loan_logs
<!-- orbit
id: "loan_logs"
icon: "📇"
collapsed: true
-->
> `loan_logs` · tabela de Relatórios
>
> Uma linha por empréstimo, com 11 colunas e cerca de 524 mil linhas.
>
> **Pertence a** [`order_logs`](#order_logs) · [`audit_links_4`](#audit_links_4) · [`notice_counts`](#notice_counts)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `loan_logs.id` · `int8` · obrigatória
- ☑️ card_language
  > `loan_logs.card_language` · `bool` · obrigatória
- 🕒 book_count
  > `loan_logs.book_count` · `timestamp` · opcional
- ☑️ vendor_count
  > `loan_logs.vendor_count` · `bool` · obrigatória
- 🕒 author_external
  > `loan_logs.author_external` · `timestamp` · obrigatória
- 🔗 book_id <!-- orbit link: "#book_items", status: "chave estrangeira" -->
  > `loan_logs.book_id` · `int8` · obrigatória
  >
  > Aponta para [`book_items`](#book_items).
- 🔢 device_label
  > `loan_logs.device_label` · `int4` · opcional
- 🧩 audit_created
  > `loan_logs.audit_created` · `jsonb` · obrigatória
- 🧩 vendor_title
  > `loan_logs.vendor_title` · `jsonb` · opcional
- ☑️ ebook_code
  > `loan_logs.ebook_code` · `bool` · obrigatória
- 🔗 event_id <!-- orbit link: "#event_history", status: "chave estrangeira" -->
  > `loan_logs.event_id` · `int8` · obrigatória
  >
  > Aponta para [`event_history`](#event_history).

### review_queues
<!-- orbit
id: "review_queues"
icon: "📇"
collapsed: true
-->
> `review_queues` · tabela de Relatórios
>
> Uma linha por resenha, com 20 colunas e cerca de 814 mil linhas.
>
> **Pertence a** [`member_counts_2`](#member_counts_2)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `review_queues.id` · `int8` · obrigatória
- 🧩 vendor_label
  > `review_queues.vendor_label` · `jsonb` · obrigatória
- 🔢 donation_archived
  > `review_queues.donation_archived` · `int4` · obrigatória
- 🔗 donation_id <!-- orbit link: "#donation_requests", status: "chave estrangeira" -->
  > `review_queues.donation_id` · `int8` · obrigatória
  >
  > Aponta para [`donation_requests`](#donation_requests).
- 🔢 event_language
  > `review_queues.event_language` · `int4` · opcional
- 🔤 hold_region
  > `review_queues.hold_region` · `varchar` · obrigatória
- 🔢 staff_active
  > `review_queues.staff_active` · `int4` · opcional
- 🔤 event_name
  > `review_queues.event_name` · `varchar` · obrigatória
- ☑️ staff_name
  > `review_queues.staff_name` · `bool` · opcional
- 🧩 author_kind
  > `review_queues.author_kind` · `jsonb` · opcional
- 🔢 staff_name_2
  > `review_queues.staff_name_2` · `int4` · opcional
- 🧩 series_name
  > `review_queues.series_name` · `jsonb` · obrigatória
- 🔗 report_id <!-- orbit link: "#report_queues_2", status: "chave estrangeira" -->
  > `review_queues.report_id` · `int8` · obrigatória
  >
  > Aponta para [`report_queues_2`](#report_queues_2).
- 🕒 review_region
  > `review_queues.review_region` · `timestamp` · obrigatória
- 🔗 staff_id <!-- orbit link: "#staff_items", status: "chave estrangeira" -->
  > `review_queues.staff_id` · `int8` · obrigatória
  >
  > Aponta para [`staff_items`](#staff_items).
- 🔤 vendor_total
  > `review_queues.vendor_total` · `varchar` · opcional
- 🔤 tag_kind
  > `review_queues.tag_kind` · `varchar` · opcional
- 🔗 author_id <!-- orbit link: "#author_links_3", status: "chave estrangeira" -->
  > `review_queues.author_id` · `int8` · obrigatória
  >
  > Aponta para [`author_links_3`](#author_links_3).
- 🧩 budget_count
  > `review_queues.budget_count` · `jsonb` · opcional
- 🔗 staff_id_2 <!-- orbit link: "#staff_counts", status: "chave estrangeira" -->
  > `review_queues.staff_id_2` · `int8` · obrigatória
  >
  > Aponta para [`staff_counts`](#staff_counts).

### tag_settings_2
<!-- orbit
id: "tag_settings_2"
icon: "🧷"
collapsed: true
-->
> `tag_settings_2` · tabela de Relatórios
>
> Uma linha por etiqueta, com 18 colunas e cerca de 409 mil linhas.
>
> **Pertence a** [`visit_queues`](#visit_queues)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `tag_settings_2.id` · `int8` · obrigatória
- 🔗 book_id <!-- orbit link: "#book_items", status: "chave estrangeira" -->
  > `tag_settings_2.book_id` · `int8` · obrigatória
  >
  > Aponta para [`book_items`](#book_items).
- 🧩 card_region
  > `tag_settings_2.card_region` · `jsonb` · obrigatória
- 🔗 card_id <!-- orbit link: "#card_queues", status: "chave estrangeira" -->
  > `tag_settings_2.card_id` · `int8` · obrigatória
  >
  > Aponta para [`card_queues`](#card_queues).
- 🔢 event_updated
  > `tag_settings_2.event_updated` · `int4` · opcional
- ☑️ loan_external
  > `tag_settings_2.loan_external` · `bool` · opcional
- 🕒 hold_kind
  > `tag_settings_2.hold_kind` · `timestamp` · obrigatória
- 🔤 device_notes
  > `tag_settings_2.device_notes` · `varchar` · opcional
- ☑️ branch_active
  > `tag_settings_2.branch_active` · `bool` · obrigatória
- ☑️ member_public
  > `tag_settings_2.member_public` · `bool` · obrigatória
- 🕒 hold_ended
  > `tag_settings_2.hold_ended` · `timestamp` · obrigatória
- 🔢 payment_title
  > `tag_settings_2.payment_title` · `int4` · obrigatória
- 🧩 fee_updated
  > `tag_settings_2.fee_updated` · `jsonb` · obrigatória
- ☑️ budget_public
  > `tag_settings_2.budget_public` · `bool` · opcional
- 🕒 budget_position
  > `tag_settings_2.budget_position` · `timestamp` · obrigatória
- 🔢 review_active
  > `tag_settings_2.review_active` · `int4` · opcional
- ☑️ visit_active
  > `tag_settings_2.visit_active` · `bool` · obrigatória
- 🔗 shelf_id <!-- orbit link: "#shelf_counts", status: "chave estrangeira" -->
  > `tag_settings_2.shelf_id` · `int8` · obrigatória
  >
  > Aponta para [`shelf_counts`](#shelf_counts).

### series_history
<!-- orbit
id: "series_history"
icon: "🧷"
collapsed: true
-->
> `series_history` · tabela de Relatórios
>
> Uma linha por série, com 17 colunas e cerca de 267 mil linhas.
>
> **Pertence a** [`review_items`](#review_items) · [`author_history`](#author_history) · [`tag_settings_2`](#tag_settings_2) · [`member_history`](#member_history)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `series_history.id` · `int8` · obrigatória
- 🧩 license_kind
  > `series_history.license_kind` · `jsonb` · opcional
- 🔤 book_kind
  > `series_history.book_kind` · `varchar` · obrigatória
- 🧩 repair_region
  > `series_history.repair_region` · `jsonb` · obrigatória
- 🕒 author_ended
  > `series_history.author_ended` · `timestamp` · obrigatória
- ☑️ copy_region
  > `series_history.copy_region` · `bool` · obrigatória
- 🔗 loan_id <!-- orbit link: "#loan_history", status: "chave estrangeira" -->
  > `series_history.loan_id` · `int8` · obrigatória
  >
  > Aponta para [`loan_history`](#loan_history).
- 🔤 order_notes
  > `series_history.order_notes` · `varchar` · obrigatória
- ☑️ room_priority
  > `series_history.room_priority` · `bool` · obrigatória
- 🔗 event_id <!-- orbit link: "#event_history", status: "chave estrangeira" -->
  > `series_history.event_id` · `int8` · obrigatória
  >
  > Aponta para [`event_history`](#event_history).
- 🧩 staff_started
  > `series_history.staff_started` · `jsonb` · obrigatória
- 🔗 ebook_id <!-- orbit link: "#ebook_queues", status: "chave estrangeira" -->
  > `series_history.ebook_id` · `int8` · obrigatória
  >
  > Aponta para [`ebook_queues`](#ebook_queues).
- 🕒 ebook_started
  > `series_history.ebook_started` · `timestamp` · obrigatória
- 🔢 vendor_region
  > `series_history.vendor_region` · `int4` · opcional
- ☑️ tag_code
  > `series_history.tag_code` · `bool` · opcional
- 🔗 copy_id <!-- orbit link: "#copy_versions", status: "chave estrangeira" -->
  > `series_history.copy_id` · `int8` · obrigatória
  >
  > Aponta para [`copy_versions`](#copy_versions).
- 🧩 audit_archived
  > `series_history.audit_archived` · `jsonb` · obrigatória

## 🔐 Equipe e acessos
<!-- orbit
id: "area-staff-access"
color: "#9333EA"
-->
> 12 tabelas. As maiores: [`order_links`](#order_links), [`license_logs_2`](#license_logs_2), [`payment_requests`](#payment_requests).
>
> **Relacionada a** [`donations`](#donations) · [`review_logs`](#review_logs) · [`budget_queues`](#budget_queues)

### order_links
<!-- orbit
id: "order_links"
icon: "📇"
collapsed: true
-->
> `order_links` · tabela de Equipe e acessos
>
> Uma linha por pedido, com 18 colunas e cerca de 360 mil linhas.
>
> **Pertence a** [`loan_settings`](#loan_settings) · [`review_logs`](#review_logs) · [`staff_counts`](#staff_counts)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `order_links.id` · `int8` · obrigatória
- 🔢 device_external
  > `order_links.device_external` · `int4` · opcional
- 🧩 report_notes
  > `order_links.report_notes` · `jsonb` · opcional
- 🧩 repair_code
  > `order_links.repair_code` · `jsonb` · obrigatória
- 🔗 fee_id <!-- orbit link: "#fee_requests", status: "chave estrangeira" -->
  > `order_links.fee_id` · `int8` · obrigatória
  >
  > Aponta para [`fee_requests`](#fee_requests).
- ☑️ notice_language
  > `order_links.notice_language` · `bool` · opcional
- 🔤 branch_public
  > `order_links.branch_public` · `varchar` · obrigatória
- 🕒 fee_source
  > `order_links.fee_source` · `timestamp` · obrigatória
- 🔢 card_archived
  > `order_links.card_archived` · `int4` · opcional
- 🧩 card_external
  > `order_links.card_external` · `jsonb` · opcional
- 🕒 room_active
  > `order_links.room_active` · `timestamp` · opcional
- 🕒 member_total
  > `order_links.member_total` · `timestamp` · obrigatória
- 🔤 budget_title
  > `order_links.budget_title` · `varchar` · opcional
- 🧩 room_count
  > `order_links.room_count` · `jsonb` · opcional
- 🧩 room_started
  > `order_links.room_started` · `jsonb` · obrigatória
- ☑️ ebook_label
  > `order_links.ebook_label` · `bool` · opcional
- ☑️ audit_archived
  > `order_links.audit_archived` · `bool` · opcional
- 🔗 order_id <!-- orbit link: "#order_versions", status: "chave estrangeira" -->
  > `order_links.order_id` · `int8` · obrigatória
  >
  > Aponta para [`order_versions`](#order_versions).

### license_logs_2
<!-- orbit
id: "license_logs_2"
icon: "📗"
collapsed: true
-->
> `license_logs_2` · tabela de Equipe e acessos
>
> Uma linha por licença, com 19 colunas e cerca de 515 mil linhas.
>
> **Pertence a** [`author_links_3`](#author_links_3) · [`order_items`](#order_items) · [`series_requests`](#series_requests) · [`room_counts_2`](#room_counts_2)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `license_logs_2.id` · `int8` · obrigatória
- ☑️ member_language
  > `license_logs_2.member_language` · `bool` · opcional
- 🔗 card_id <!-- orbit link: "#card_links_2", status: "chave estrangeira" -->
  > `license_logs_2.card_id` · `int8` · obrigatória
  >
  > Aponta para [`card_links_2`](#card_links_2).
- 🔢 member_reviewed
  > `license_logs_2.member_reviewed` · `int4` · opcional
- 🧩 book_ended
  > `license_logs_2.book_ended` · `jsonb` · opcional
- 🔗 report_id <!-- orbit link: "#report_queues", status: "chave estrangeira" -->
  > `license_logs_2.report_id` · `int8` · obrigatória
  >
  > Aponta para [`report_queues`](#report_queues).
- 🕒 order_notes
  > `license_logs_2.order_notes` · `timestamp` · obrigatória
- 🔗 visit_id <!-- orbit link: "#visit_links", status: "chave estrangeira" -->
  > `license_logs_2.visit_id` · `int8` · obrigatória
  >
  > Aponta para [`visit_links`](#visit_links).
- 🔤 budget_total
  > `license_logs_2.budget_total` · `varchar` · obrigatória
- 🧩 tag_position
  > `license_logs_2.tag_position` · `jsonb` · opcional
- 🔢 tag_public
  > `license_logs_2.tag_public` · `int4` · obrigatória
- ☑️ audit_reviewed
  > `license_logs_2.audit_reviewed` · `bool` · obrigatória
- ☑️ card_code
  > `license_logs_2.card_code` · `bool` · opcional
- 🕒 card_label
  > `license_logs_2.card_label` · `timestamp` · opcional
- ☑️ book_state
  > `license_logs_2.book_state` · `bool` · obrigatória
- 🔢 loan_kind
  > `license_logs_2.loan_kind` · `int4` · opcional
- ☑️ order_count
  > `license_logs_2.order_count` · `bool` · opcional
- 🧩 event_state
  > `license_logs_2.event_state` · `jsonb` · opcional
- 🔤 fee_format
  > `license_logs_2.fee_format` · `varchar` · obrigatória

### payment_requests
<!-- orbit
id: "payment_requests"
icon: "🗃️"
collapsed: true
-->
> `payment_requests` · tabela de Equipe e acessos
>
> Uma linha por pagamento, com 21 colunas e cerca de 372 mil linhas.
>
> **Pertence a** [`card_logs`](#card_logs) · [`license_versions`](#license_versions) · [`vendor_counts`](#vendor_counts) · [`member_counts`](#member_counts)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `payment_requests.id` · `int8` · obrigatória
- ☑️ review_position
  > `payment_requests.review_position` · `bool` · obrigatória
- ☑️ donation_label
  > `payment_requests.donation_label` · `bool` · opcional
- 🕒 vendor_notes
  > `payment_requests.vendor_notes` · `timestamp` · opcional
- 🔗 donation_id <!-- orbit link: "#donation_history", status: "chave estrangeira" -->
  > `payment_requests.donation_id` · `int8` · obrigatória
  >
  > Aponta para [`donation_history`](#donation_history).
- 🔢 order_notes
  > `payment_requests.order_notes` · `int4` · opcional
- 🔢 visit_code
  > `payment_requests.visit_code` · `int4` · opcional
- 🧩 hold_code
  > `payment_requests.hold_code` · `jsonb` · opcional
- 🔢 order_code
  > `payment_requests.order_code` · `int4` · obrigatória
- 🔗 events_id <!-- orbit link: "#events", status: "chave estrangeira" -->
  > `payment_requests.events_id` · `int8` · obrigatória
  >
  > Aponta para [`events`](#events).
- 🔗 payment_id <!-- orbit link: "#payment_requests", status: "chave estrangeira" -->
  > `payment_requests.payment_id` · `int8` · obrigatória
  >
  > Aponta para [`payment_requests`](#payment_requests).
- ☑️ member_active
  > `payment_requests.member_active` · `bool` · obrigatória
- 🔢 staff_state
  > `payment_requests.staff_state` · `int4` · opcional
- 🕒 budget_label
  > `payment_requests.budget_label` · `timestamp` · opcional
- 🔤 member_reviewed
  > `payment_requests.member_reviewed` · `varchar` · opcional
- 🔤 card_status
  > `payment_requests.card_status` · `varchar` · opcional
- 🔤 budget_language
  > `payment_requests.budget_language` · `varchar` · obrigatória
- 🧩 review_language
  > `payment_requests.review_language` · `jsonb` · opcional
- 🕒 event_external
  > `payment_requests.event_external` · `timestamp` · obrigatória
- 🔤 review_kind
  > `payment_requests.review_kind` · `varchar` · obrigatória
- 🕒 book_state
  > `payment_requests.book_state` · `timestamp` · opcional

### report_links
<!-- orbit
id: "report_links"
icon: "🗃️"
collapsed: true
-->
> `report_links` · tabela de Equipe e acessos
>
> Uma linha por relatório, com 17 colunas e cerca de 417 mil linhas.
>
> **Pertence a** [`order_items`](#order_items) · [`audit_links`](#audit_links) · [`room_counts`](#room_counts)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `report_links.id` · `int8` · obrigatória
- ☑️ staff_public
  > `report_links.staff_public` · `bool` · opcional
- 🧩 card_region
  > `report_links.card_region` · `jsonb` · opcional
- 🕒 donation_status
  > `report_links.donation_status` · `timestamp` · obrigatória
- 🔗 report_id <!-- orbit link: "#report_queues", status: "chave estrangeira" -->
  > `report_links.report_id` · `int8` · obrigatória
  >
  > Aponta para [`report_queues`](#report_queues).
- 🕒 tag_state
  > `report_links.tag_state` · `timestamp` · opcional
- 🕒 review_language
  > `report_links.review_language` · `timestamp` · opcional
- 🔗 seriess_id <!-- orbit link: "#seriess", status: "chave estrangeira" -->
  > `report_links.seriess_id` · `int8` · obrigatória
  >
  > Aponta para [`seriess`](#seriess).
- 🔤 device_total
  > `report_links.device_total` · `varchar` · obrigatória
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
  > `report_links.donation_region` · `jsonb` · obrigatória
- 🔤 copy_count
  > `report_links.copy_count` · `varchar` · obrigatória
- 🔗 donation_id <!-- orbit link: "#donation_links", status: "chave estrangeira" -->
  > `report_links.donation_id` · `int8` · obrigatória
  >
  > Aponta para [`donation_links`](#donation_links).

### member_history
<!-- orbit
id: "member_history"
icon: "📇"
collapsed: true
-->
> `member_history` · tabela de Equipe e acessos
>
> Uma linha por leitor, com 23 colunas e cerca de 91 mil linhas.
>
> **Pertence a** [`staff_settings`](#staff_settings) · [`staff_counts`](#staff_counts) · [`vendor_history`](#vendor_history) · [`hold_links_2`](#hold_links_2)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `member_history.id` · `int8` · obrigatória
- 🔢 budget_notes
  > `member_history.budget_notes` · `int4` · obrigatória
- 🧩 staff_priority
  > `member_history.staff_priority` · `jsonb` · opcional
- 🕒 order_public
  > `member_history.order_public` · `timestamp` · obrigatória
- 🧩 review_amount
  > `member_history.review_amount` · `jsonb` · obrigatória
- 🕒 member_created
  > `member_history.member_created` · `timestamp` · opcional
- 🕒 report_amount
  > `member_history.report_amount` · `timestamp` · obrigatória
- 🧩 visit_language
  > `member_history.visit_language` · `jsonb` · obrigatória
- 🧩 copy_active
  > `member_history.copy_active` · `jsonb` · opcional
- 🕒 audit_status
  > `member_history.audit_status` · `timestamp` · obrigatória
- 🔤 repair_notes
  > `member_history.repair_notes` · `varchar` · opcional
- ☑️ order_total
  > `member_history.order_total` · `bool` · obrigatória
- ☑️ license_active
  > `member_history.license_active` · `bool` · opcional
- ☑️ card_reviewed
  > `member_history.card_reviewed` · `bool` · opcional
- 🕒 loan_active
  > `member_history.loan_active` · `timestamp` · obrigatória
- ☑️ loan_total
  > `member_history.loan_total` · `bool` · opcional
- 🧩 copy_region
  > `member_history.copy_region` · `jsonb` · obrigatória
- 🔤 audit_ended
  > `member_history.audit_ended` · `varchar` · obrigatória
- ☑️ payment_code
  > `member_history.payment_code` · `bool` · opcional
- 🧩 member_external
  > `member_history.member_external` · `jsonb` · opcional
- 🔤 review_format
  > `member_history.review_format` · `varchar` · opcional
- 🔤 ebook_source
  > `member_history.ebook_source` · `varchar` · obrigatória
- 🔤 branch_ended
  > `member_history.branch_ended` · `varchar` · opcional

### vendor_settings
<!-- orbit
id: "vendor_settings"
icon: "📇"
collapsed: true
-->
> `vendor_settings` · tabela de Equipe e acessos
>
> Uma linha por fornecedor, com 10 colunas e cerca de 237 mil linhas.
>
> **Pertence a** [`fee_history`](#fee_history) · [`notice_counts_2`](#notice_counts_2) · [`shelfs`](#shelfs)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `vendor_settings.id` · `int8` · obrigatória
- 🔤 visit_updated
  > `vendor_settings.visit_updated` · `varchar` · obrigatória
- 🔗 audit_id <!-- orbit link: "#audit_queues", status: "chave estrangeira" -->
  > `vendor_settings.audit_id` · `int8` · obrigatória
  >
  > Aponta para [`audit_queues`](#audit_queues).
- 🔗 vendor_id <!-- orbit link: "#vendor_items", status: "chave estrangeira" -->
  > `vendor_settings.vendor_id` · `int8` · obrigatória
  >
  > Aponta para [`vendor_items`](#vendor_items).
- 🕒 role_archived
  > `vendor_settings.role_archived` · `timestamp` · opcional
- 🧩 vendor_reviewed
  > `vendor_settings.vendor_reviewed` · `jsonb` · obrigatória
- 🔗 copy_id <!-- orbit link: "#copy_items", status: "chave estrangeira" -->
  > `vendor_settings.copy_id` · `int8` · obrigatória
  >
  > Aponta para [`copy_items`](#copy_items).
- 🔗 budgets_id <!-- orbit link: "#budgets", status: "chave estrangeira" -->
  > `vendor_settings.budgets_id` · `int8` · obrigatória
  >
  > Aponta para [`budgets`](#budgets).
- 🔗 series_id <!-- orbit link: "#series_counts_2", status: "chave estrangeira" -->
  > `vendor_settings.series_id` · `int8` · obrigatória
  >
  > Aponta para [`series_counts_2`](#series_counts_2).
- 🧩 series_reviewed
  > `vendor_settings.series_reviewed` · `jsonb` · opcional

### copy_counts
<!-- orbit
id: "copy_counts"
icon: "📇"
collapsed: true
-->
> `copy_counts` · tabela de Equipe e acessos
>
> Uma linha por exemplar, com 13 colunas e cerca de 414 mil linhas.
>
> **Pertence a** [`room_counts_2`](#room_counts_2)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `copy_counts.id` · `int8` · obrigatória
- 🕒 staff_public
  > `copy_counts.staff_public` · `timestamp` · obrigatória
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
- 🔗 payment_id <!-- orbit link: "#payment_settings", status: "chave estrangeira" -->
  > `copy_counts.payment_id` · `int8` · obrigatória
  >
  > Aponta para [`payment_settings`](#payment_settings).
- 🔗 staff_id <!-- orbit link: "#staff_counts", status: "chave estrangeira" -->
  > `copy_counts.staff_id` · `int8` · obrigatória
  >
  > Aponta para [`staff_counts`](#staff_counts).
- 🔤 audit_label
  > `copy_counts.audit_label` · `varchar` · obrigatória
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
> `order_logs` · tabela de Equipe e acessos
>
> Uma linha por pedido, com 21 colunas e cerca de 184 mil linhas.
>
> **Pertence a** [`hold_links_2`](#hold_links_2) · [`series_counts_2`](#series_counts_2) · [`audit_settings`](#audit_settings)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `order_logs.id` · `int8` · obrigatória
- 🕒 branch_updated
  > `order_logs.branch_updated` · `timestamp` · opcional
- 🕒 report_started
  > `order_logs.report_started` · `timestamp` · opcional
- 🧩 device_count
  > `order_logs.device_count` · `jsonb` · opcional
- 🔤 branch_amount
  > `order_logs.branch_amount` · `varchar` · obrigatória
- 🕒 donation_total
  > `order_logs.donation_total` · `timestamp` · obrigatória
- 🧩 book_amount
  > `order_logs.book_amount` · `jsonb` · opcional
- 🧩 fee_total
  > `order_logs.fee_total` · `jsonb` · obrigatória
- 🔢 donation_ended
  > `order_logs.donation_ended` · `int4` · opcional
- 🕒 staff_region
  > `order_logs.staff_region` · `timestamp` · obrigatória
- ☑️ budget_code
  > `order_logs.budget_code` · `bool` · obrigatória
- ☑️ copy_kind
  > `order_logs.copy_kind` · `bool` · obrigatória
- 🔢 tag_notes
  > `order_logs.tag_notes` · `int4` · obrigatória
- 🔤 audit_external
  > `order_logs.audit_external` · `varchar` · obrigatória
- 🕒 audit_title
  > `order_logs.audit_title` · `timestamp` · obrigatória
- 🧩 vendor_language
  > `order_logs.vendor_language` · `jsonb` · opcional
- 🔢 tag_total
  > `order_logs.tag_total` · `int4` · opcional
- 🕒 vendor_reviewed
  > `order_logs.vendor_reviewed` · `timestamp` · opcional
- 🔤 notice_language
  > `order_logs.notice_language` · `varchar` · opcional
- 🔗 vendor_id <!-- orbit link: "#vendor_settings", status: "chave estrangeira" -->
  > `order_logs.vendor_id` · `int8` · obrigatória
  >
  > Aponta para [`vendor_settings`](#vendor_settings).
- 🧩 tag_region
  > `order_logs.tag_region` · `jsonb` · opcional

### report_versions
<!-- orbit
id: "report_versions"
icon: "📇"
collapsed: true
-->
> `report_versions` · tabela de Equipe e acessos
>
> Uma linha por relatório, com 7 colunas e cerca de 266 mil linhas.
>
> **Pertence a** [`series_links`](#series_links) · [`loan_counts`](#loan_counts) · [`series_counts_2`](#series_counts_2)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `report_versions.id` · `int8` · obrigatória
- 🔤 ebook_notes
  > `report_versions.ebook_notes` · `varchar` · obrigatória
- 🔤 staff_started
  > `report_versions.staff_started` · `varchar` · obrigatória
- 🧩 budget_source
  > `report_versions.budget_source` · `jsonb` · obrigatória
- 🕒 order_language
  > `report_versions.order_language` · `timestamp` · obrigatória
- ☑️ visit_title
  > `report_versions.visit_title` · `bool` · opcional
- 🧩 card_ended
  > `report_versions.card_ended` · `jsonb` · obrigatória

### holds_2
<!-- orbit
id: "holds_2"
icon: "🧷"
collapsed: true
-->
> `holds_2` · tabela de Equipe e acessos
>
> Uma linha por reserva, com 18 colunas e cerca de 340 mil linhas.
>
> **Pertence a** [`order_logs`](#order_logs) · [`report_queues_2`](#report_queues_2) · [`card_queues`](#card_queues) · [`tag_counts`](#tag_counts)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `holds_2.id` · `int8` · obrigatória
- 🔗 device_id <!-- orbit link: "#device_settings", status: "chave estrangeira" -->
  > `holds_2.device_id` · `int8` · obrigatória
  >
  > Aponta para [`device_settings`](#device_settings).
- 🧩 room_archived
  > `holds_2.room_archived` · `jsonb` · opcional
- ☑️ report_public
  > `holds_2.report_public` · `bool` · obrigatória
- 🔢 branch_format
  > `holds_2.branch_format` · `int4` · opcional
- 🔤 order_status
  > `holds_2.order_status` · `varchar` · opcional
- 🕒 donation_updated
  > `holds_2.donation_updated` · `timestamp` · opcional
- ☑️ shelf_kind
  > `holds_2.shelf_kind` · `bool` · opcional
- 🔗 copy_id <!-- orbit link: "#copy_items", status: "chave estrangeira" -->
  > `holds_2.copy_id` · `int8` · obrigatória
  >
  > Aponta para [`copy_items`](#copy_items).
- 🔢 payment_language
  > `holds_2.payment_language` · `int4` · opcional
- 🧩 member_source
  > `holds_2.member_source` · `jsonb` · opcional
- 🕒 room_external
  > `holds_2.room_external` · `timestamp` · obrigatória
- 🔢 shelf_active
  > `holds_2.shelf_active` · `int4` · opcional
- ☑️ payment_public
  > `holds_2.payment_public` · `bool` · obrigatória
- 🧩 shelf_total
  > `holds_2.shelf_total` · `jsonb` · obrigatória
- 🔤 donation_code
  > `holds_2.donation_code` · `varchar` · obrigatória
- 🔤 series_priority
  > `holds_2.series_priority` · `varchar` · opcional
- 🔗 vendor_id <!-- orbit link: "#vendor_settings", status: "chave estrangeira" -->
  > `holds_2.vendor_id` · `int8` · obrigatória
  >
  > Aponta para [`vendor_settings`](#vendor_settings).

### branchs
<!-- orbit
id: "branchs"
icon: "🗂️"
collapsed: true
-->
> `branchs` · tabela de Equipe e acessos
>
> Uma linha por unidade, com 14 colunas e cerca de 619 mil linhas.
>
> **Pertence a** [`staff_counts`](#staff_counts) · [`staff_logs`](#staff_logs) · [`room_requests`](#room_requests)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `branchs.id` · `int8` · obrigatória
- 🕒 report_ended
  > `branchs.report_ended` · `timestamp` · obrigatória
- 🕒 staff_archived
  > `branchs.staff_archived` · `timestamp` · obrigatória
- 🔗 report_id <!-- orbit link: "#report_items", status: "chave estrangeira" -->
  > `branchs.report_id` · `int8` · obrigatória
  >
  > Aponta para [`report_items`](#report_items).
- 🔤 member_active
  > `branchs.member_active` · `varchar` · obrigatória
- 🔤 hold_source
  > `branchs.hold_source` · `varchar` · opcional
- 🔢 notice_source
  > `branchs.notice_source` · `int4` · obrigatória
- ☑️ hold_format
  > `branchs.hold_format` · `bool` · opcional
- ☑️ order_source
  > `branchs.order_source` · `bool` · obrigatória
- 🕒 copy_status
  > `branchs.copy_status` · `timestamp` · opcional
- 🔤 review_format
  > `branchs.review_format` · `varchar` · obrigatória
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
> `series_links` · tabela de Equipe e acessos
>
> Uma linha por série, com 23 colunas e cerca de 436 mil linhas.
>
> **Pertence a** [`book_links`](#book_links)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `series_links.id` · `int8` · obrigatória
- 🔤 shelf_ended
  > `series_links.shelf_ended` · `varchar` · obrigatória
- ☑️ visit_position
  > `series_links.visit_position` · `bool` · opcional
- 🕒 report_priority
  > `series_links.report_priority` · `timestamp` · obrigatória
- 🧩 author_kind
  > `series_links.author_kind` · `jsonb` · opcional
- ☑️ donation_format
  > `series_links.donation_format` · `bool` · obrigatória
- 🔤 shelf_created
  > `series_links.shelf_created` · `varchar` · obrigatória
- 🧩 repair_public
  > `series_links.repair_public` · `jsonb` · opcional
- ☑️ event_source
  > `series_links.event_source` · `bool` · obrigatória
- ☑️ fee_count
  > `series_links.fee_count` · `bool` · obrigatória
- 🔤 notice_started
  > `series_links.notice_started` · `varchar` · opcional
- ☑️ vendor_position
  > `series_links.vendor_position` · `bool` · opcional
- 🕒 device_count
  > `series_links.device_count` · `timestamp` · obrigatória
- 🔗 donation_id <!-- orbit link: "#donation_links", status: "chave estrangeira" -->
  > `series_links.donation_id` · `int8` · obrigatória
  >
  > Aponta para [`donation_links`](#donation_links).
- 🕒 ebook_reviewed
  > `series_links.ebook_reviewed` · `timestamp` · opcional
- ☑️ license_kind
  > `series_links.license_kind` · `bool` · opcional
- 🔢 review_archived
  > `series_links.review_archived` · `int4` · obrigatória
- 🔢 member_status
  > `series_links.member_status` · `int4` · opcional
- ☑️ loan_source
  > `series_links.loan_source` · `bool` · obrigatória
- 🔗 member_id <!-- orbit link: "#member_versions", status: "chave estrangeira" -->
  > `series_links.member_id` · `int8` · obrigatória
  >
  > Aponta para [`member_versions`](#member_versions).
- 🔢 tag_public
  > `series_links.tag_public` · `int4` · opcional
- 🕒 book_region
  > `series_links.book_region` · `timestamp` · opcional
- 🔢 hold_position
  > `series_links.hold_position` · `int4` · obrigatória

## 📨 Avisos
<!-- orbit
id: "area-notices"
color: "#DB2777"
-->
> 10 tabelas. As maiores: [`ebook_requests`](#ebook_requests), [`card_links_2`](#card_links_2), [`review_counts`](#review_counts).
>
> **Relacionada a** [`order_items`](#order_items) · [`hold_links_2`](#hold_links_2) · [`book_links`](#book_links)

### ebook_requests
<!-- orbit
id: "ebook_requests"
icon: "🗂️"
collapsed: true
-->
> `ebook_requests` · tabela de Avisos
>
> Uma linha por livro digital, com 6 colunas e cerca de 437 mil linhas.
>
> **Pertence a** [`review_logs`](#review_logs) · [`card_links_2`](#card_links_2) · [`event_history`](#event_history) · [`audit_queues`](#audit_queues)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `ebook_requests.id` · `int8` · obrigatória
- 🔤 series_code
  > `ebook_requests.series_code` · `varchar` · opcional
- 🔗 branch_id <!-- orbit link: "#branch_links", status: "chave estrangeira" -->
  > `ebook_requests.branch_id` · `int8` · obrigatória
  >
  > Aponta para [`branch_links`](#branch_links).
- 🧩 fee_code
  > `ebook_requests.fee_code` · `jsonb` · obrigatória
- ☑️ member_region
  > `ebook_requests.member_region` · `bool` · obrigatória
- 🔗 visit_id <!-- orbit link: "#visit_items", status: "chave estrangeira" -->
  > `ebook_requests.visit_id` · `int8` · obrigatória
  >
  > Aponta para [`visit_items`](#visit_items).

### card_links_2
<!-- orbit
id: "card_links_2"
icon: "🧷"
collapsed: true
-->
> `card_links_2` · tabela de Avisos
>
> Uma linha por carteirinha, com 13 colunas e cerca de 205 mil linhas.
>
> **Pertence a** [`ebook_logs`](#ebook_logs)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `card_links_2.id` · `int8` · obrigatória
- 🔗 review_id <!-- orbit link: "#review_links", status: "chave estrangeira" -->
  > `card_links_2.review_id` · `int8` · obrigatória
  >
  > Aponta para [`review_links`](#review_links).
- 🔢 ebook_language
  > `card_links_2.ebook_language` · `int4` · opcional
- 🕒 fee_archived
  > `card_links_2.fee_archived` · `timestamp` · obrigatória
- 🕒 loan_kind
  > `card_links_2.loan_kind` · `timestamp` · obrigatória
- 🕒 review_format
  > `card_links_2.review_format` · `timestamp` · obrigatória
- 🔢 order_kind
  > `card_links_2.order_kind` · `int4` · opcional
- 🔗 review_id_2 <!-- orbit link: "#review_counts", status: "chave estrangeira" -->
  > `card_links_2.review_id_2` · `int8` · obrigatória
  >
  > Aponta para [`review_counts`](#review_counts).
- ☑️ loan_kind_2
  > `card_links_2.loan_kind_2` · `bool` · obrigatória
- ☑️ member_notes
  > `card_links_2.member_notes` · `bool` · obrigatória
- ☑️ report_format
  > `card_links_2.report_format` · `bool` · obrigatória
- 🔤 fee_format
  > `card_links_2.fee_format` · `varchar` · obrigatória
- 🔗 payment_id <!-- orbit link: "#payment_settings", status: "chave estrangeira" -->
  > `card_links_2.payment_id` · `int8` · obrigatória
  >
  > Aponta para [`payment_settings`](#payment_settings).

### review_counts
<!-- orbit
id: "review_counts"
icon: "📗"
collapsed: true
-->
> `review_counts` · tabela de Avisos
>
> Uma linha por resenha, com 16 colunas e cerca de 486 mil linhas.
>
> **Pertence a** [`payment_versions`](#payment_versions) · [`report_versions`](#report_versions)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `review_counts.id` · `int8` · obrigatória
- 🔤 book_notes
  > `review_counts.book_notes` · `varchar` · opcional
- 🕒 loan_public
  > `review_counts.loan_public` · `timestamp` · opcional
- 🧩 donation_title
  > `review_counts.donation_title` · `jsonb` · obrigatória
- ☑️ author_count
  > `review_counts.author_count` · `bool` · obrigatória
- ☑️ copy_reviewed
  > `review_counts.copy_reviewed` · `bool` · obrigatória
- 🔗 report_id <!-- orbit link: "#report_queues", status: "chave estrangeira" -->
  > `review_counts.report_id` · `int8` · obrigatória
  >
  > Aponta para [`report_queues`](#report_queues).
- ☑️ series_amount
  > `review_counts.series_amount` · `bool` · opcional
- 🔢 member_status
  > `review_counts.member_status` · `int4` · opcional
- 🔗 review_id <!-- orbit link: "#review_links", status: "chave estrangeira" -->
  > `review_counts.review_id` · `int8` · obrigatória
  >
  > Aponta para [`review_links`](#review_links).
- ☑️ loan_source
  > `review_counts.loan_source` · `bool` · opcional
- 🔤 donation_started
  > `review_counts.donation_started` · `varchar` · obrigatória
- 🔢 budget_external
  > `review_counts.budget_external` · `int4` · opcional
- 🔗 loan_id <!-- orbit link: "#loan_logs", status: "chave estrangeira" -->
  > `review_counts.loan_id` · `int8` · obrigatória
  >
  > Aponta para [`loan_logs`](#loan_logs).
- 🔤 author_archived
  > `review_counts.author_archived` · `varchar` · obrigatória
- 🧩 audit_reviewed
  > `review_counts.audit_reviewed` · `jsonb` · opcional

### vendor_counts
<!-- orbit
id: "vendor_counts"
icon: "🧮"
collapsed: true
-->
> `vendor_counts` · tabela de Avisos
>
> Uma linha por fornecedor, com 16 colunas e cerca de 78 mil linhas.
>
> **Pertence a** [`loan_settings`](#loan_settings)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `vendor_counts.id` · `int8` · obrigatória
- 🔢 room_format
  > `vendor_counts.room_format` · `int4` · obrigatória
- 🔤 budget_kind
  > `vendor_counts.budget_kind` · `varchar` · obrigatória
- 🧩 report_format
  > `vendor_counts.report_format` · `jsonb` · obrigatória
- 🔗 audit_id <!-- orbit link: "#audit_links_2", status: "chave estrangeira" -->
  > `vendor_counts.audit_id` · `int8` · obrigatória
  >
  > Aponta para [`audit_links_2`](#audit_links_2).
- ☑️ budget_count
  > `vendor_counts.budget_count` · `bool` · obrigatória
- ☑️ book_title
  > `vendor_counts.book_title` · `bool` · opcional
- 🔢 hold_amount
  > `vendor_counts.hold_amount` · `int4` · opcional
- 🕒 tag_archived
  > `vendor_counts.tag_archived` · `timestamp` · obrigatória
- 🔤 review_active
  > `vendor_counts.review_active` · `varchar` · obrigatória
- 🧩 review_count
  > `vendor_counts.review_count` · `jsonb` · obrigatória
- 🧩 repair_created
  > `vendor_counts.repair_created` · `jsonb` · obrigatória
- 🔗 series_id <!-- orbit link: "#series_counts", status: "chave estrangeira" -->
  > `vendor_counts.series_id` · `int8` · obrigatória
  >
  > Aponta para [`series_counts`](#series_counts).
- 🧩 notice_active
  > `vendor_counts.notice_active` · `jsonb` · obrigatória
- 🔢 role_reviewed
  > `vendor_counts.role_reviewed` · `int4` · obrigatória
- 🔗 budgets_id <!-- orbit link: "#budgets", status: "chave estrangeira" -->
  > `vendor_counts.budgets_id` · `int8` · obrigatória
  >
  > Aponta para [`budgets`](#budgets).

### staff_settings
<!-- orbit
id: "staff_settings"
icon: "🗃️"
collapsed: true
-->
> `staff_settings` · tabela de Avisos
>
> Uma linha por funcionário, com 20 colunas e cerca de 732 mil linhas.
>
> **Pertence a** [`ebook_requests`](#ebook_requests)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `staff_settings.id` · `int8` · obrigatória
- 🔢 tag_kind
  > `staff_settings.tag_kind` · `int4` · obrigatória
- 🧩 vendor_external
  > `staff_settings.vendor_external` · `jsonb` · obrigatória
- 🕒 report_external
  > `staff_settings.report_external` · `timestamp` · obrigatória
- 🔗 audit_id <!-- orbit link: "#audit_links_2", status: "chave estrangeira" -->
  > `staff_settings.audit_id` · `int8` · obrigatória
  >
  > Aponta para [`audit_links_2`](#audit_links_2).
- ☑️ device_created
  > `staff_settings.device_created` · `bool` · opcional
- 🔢 device_state
  > `staff_settings.device_state` · `int4` · obrigatória
- 🕒 card_language
  > `staff_settings.card_language` · `timestamp` · opcional
- ☑️ member_title
  > `staff_settings.member_title` · `bool` · obrigatória
- 🔤 author_count
  > `staff_settings.author_count` · `varchar` · obrigatória
- ☑️ order_state
  > `staff_settings.order_state` · `bool` · obrigatória
- 🧩 payment_notes
  > `staff_settings.payment_notes` · `jsonb` · obrigatória
- 🕒 book_region
  > `staff_settings.book_region` · `timestamp` · obrigatória
- 🧩 report_reviewed
  > `staff_settings.report_reviewed` · `jsonb` · opcional
- 🕒 donation_ended
  > `staff_settings.donation_ended` · `timestamp` · opcional
- 🕒 repair_title
  > `staff_settings.repair_title` · `timestamp` · opcional
- 🕒 branch_ended
  > `staff_settings.branch_ended` · `timestamp` · opcional
- 🔤 room_count
  > `staff_settings.room_count` · `varchar` · obrigatória
- 🧩 license_position
  > `staff_settings.license_position` · `jsonb` · obrigatória
- 🔢 author_position
  > `staff_settings.author_position` · `int4` · obrigatória

### visit_queues
<!-- orbit
id: "visit_queues"
icon: "🗂️"
collapsed: true
-->
> `visit_queues` · tabela de Avisos
>
> Uma linha por visita, com 18 colunas e cerca de 48 mil linhas.
>
> **Pertence a** [`report_items`](#report_items) · [`shelf_counts`](#shelf_counts) · [`card_queues`](#card_queues) · [`device_settings`](#device_settings)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `visit_queues.id` · `int8` · obrigatória
- ☑️ event_state
  > `visit_queues.event_state` · `bool` · opcional
- 🧩 tag_label
  > `visit_queues.tag_label` · `jsonb` · obrigatória
- 🧩 license_state
  > `visit_queues.license_state` · `jsonb` · obrigatória
- 🔤 report_active
  > `visit_queues.report_active` · `varchar` · opcional
- 🕒 book_amount
  > `visit_queues.book_amount` · `timestamp` · opcional
- 🕒 payment_kind
  > `visit_queues.payment_kind` · `timestamp` · obrigatória
- ☑️ visit_created
  > `visit_queues.visit_created` · `bool` · obrigatória
- 🕒 tag_format
  > `visit_queues.tag_format` · `timestamp` · obrigatória
- 🔤 donation_name
  > `visit_queues.donation_name` · `varchar` · obrigatória
- 🔤 visit_notes
  > `visit_queues.visit_notes` · `varchar` · obrigatória
- ☑️ order_count
  > `visit_queues.order_count` · `bool` · obrigatória
- 🧩 device_position
  > `visit_queues.device_position` · `jsonb` · opcional
- 🕒 notice_code
  > `visit_queues.notice_code` · `timestamp` · obrigatória
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
> `loan_logs_2` · tabela de Avisos
>
> Uma linha por empréstimo, com 24 colunas e cerca de 9 mil linhas.
>
> **Pertence a** [`tag_settings`](#tag_settings) · [`series_links`](#series_links) · [`shelf_counts`](#shelf_counts)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `loan_logs_2.id` · `int8` · obrigatória
- 🔗 card_id <!-- orbit link: "#card_queues", status: "chave estrangeira" -->
  > `loan_logs_2.card_id` · `int8` · obrigatória
  >
  > Aponta para [`card_queues`](#card_queues).
- ☑️ shelf_region
  > `loan_logs_2.shelf_region` · `bool` · opcional
- 🕒 order_source
  > `loan_logs_2.order_source` · `timestamp` · opcional
- ☑️ series_name
  > `loan_logs_2.series_name` · `bool` · obrigatória
- 🕒 order_code
  > `loan_logs_2.order_code` · `timestamp` · obrigatória
- ☑️ role_amount
  > `loan_logs_2.role_amount` · `bool` · obrigatória
- 🧩 review_public
  > `loan_logs_2.review_public` · `jsonb` · obrigatória
- 🔤 loan_kind
  > `loan_logs_2.loan_kind` · `varchar` · opcional
- 🔗 card_id_2 <!-- orbit link: "#card_links", status: "chave estrangeira" -->
  > `loan_logs_2.card_id_2` · `int8` · obrigatória
  >
  > Aponta para [`card_links`](#card_links).
- 🧩 visit_active
  > `loan_logs_2.visit_active` · `jsonb` · opcional
- 🧩 fee_active
  > `loan_logs_2.fee_active` · `jsonb` · opcional
- 🔗 card_id_3 <!-- orbit link: "#card_links", status: "chave estrangeira" -->
  > `loan_logs_2.card_id_3` · `int8` · obrigatória
  >
  > Aponta para [`card_links`](#card_links).
- 🧩 vendor_language
  > `loan_logs_2.vendor_language` · `jsonb` · obrigatória
- 🧩 shelf_count
  > `loan_logs_2.shelf_count` · `jsonb` · obrigatória
- 🔗 loan_id <!-- orbit link: "#loan_counts", status: "chave estrangeira" -->
  > `loan_logs_2.loan_id` · `int8` · obrigatória
  >
  > Aponta para [`loan_counts`](#loan_counts).
- 🧩 author_kind
  > `loan_logs_2.author_kind` · `jsonb` · opcional
- 🧩 copy_ended
  > `loan_logs_2.copy_ended` · `jsonb` · obrigatória
- ☑️ member_title
  > `loan_logs_2.member_title` · `bool` · obrigatória
- 🧩 review_code
  > `loan_logs_2.review_code` · `jsonb` · obrigatória
- 🔗 licenses_id <!-- orbit link: "#licenses", status: "chave estrangeira" -->
  > `loan_logs_2.licenses_id` · `int8` · obrigatória
  >
  > Aponta para [`licenses`](#licenses).
- 🕒 card_format
  > `loan_logs_2.card_format` · `timestamp` · opcional
- 🔤 room_position
  > `loan_logs_2.room_position` · `varchar` · opcional
- ☑️ visit_amount
  > `loan_logs_2.visit_amount` · `bool` · obrigatória

### donations
<!-- orbit
id: "donations"
icon: "🗂️"
collapsed: true
-->
> `donations` · tabela de Avisos
>
> Uma linha por doação, com 19 colunas e cerca de 377 mil linhas.
>
> **Pertence a** [`budget_settings`](#budget_settings)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `donations.id` · `int8` · obrigatória
- 🔤 series_source
  > `donations.series_source` · `varchar` · opcional
- ☑️ ebook_name
  > `donations.ebook_name` · `bool` · obrigatória
- 🔗 repair_id <!-- orbit link: "#repair_requests", status: "chave estrangeira" -->
  > `donations.repair_id` · `int8` · obrigatória
  >
  > Aponta para [`repair_requests`](#repair_requests).
- 🔤 member_name
  > `donations.member_name` · `varchar` · opcional
- 🔤 audit_total
  > `donations.audit_total` · `varchar` · obrigatória
- ☑️ staff_name
  > `donations.staff_name` · `bool` · opcional
- 🔤 hold_name
  > `donations.hold_name` · `varchar` · obrigatória
- 🔢 staff_external
  > `donations.staff_external` · `int4` · obrigatória
- 🔢 report_started
  > `donations.report_started` · `int4` · opcional
- 🕒 ebook_amount
  > `donations.ebook_amount` · `timestamp` · opcional
- 🔤 ebook_active
  > `donations.ebook_active` · `varchar` · opcional
- 🕒 shelf_name
  > `donations.shelf_name` · `timestamp` · obrigatória
- 🔗 vendor_id <!-- orbit link: "#vendor_history", status: "chave estrangeira" -->
  > `donations.vendor_id` · `int8` · obrigatória
  >
  > Aponta para [`vendor_history`](#vendor_history).
- 🕒 review_updated
  > `donations.review_updated` · `timestamp` · opcional
- ☑️ role_state
  > `donations.role_state` · `bool` · opcional
- 🔗 staff_id <!-- orbit link: "#staff_counts", status: "chave estrangeira" -->
  > `donations.staff_id` · `int8` · obrigatória
  >
  > Aponta para [`staff_counts`](#staff_counts).
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
> `review_logs` · tabela de Avisos
>
> Uma linha por resenha, com 7 colunas e cerca de 143 mil linhas.
>
> **Pertence a** [`copy_versions`](#copy_versions) · [`review_counts`](#review_counts) · [`donation_history`](#donation_history) · [`tags`](#tags)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `review_logs.id` · `int8` · obrigatória
- 🧩 license_position
  > `review_logs.license_position` · `jsonb` · opcional
- 🧩 staff_count
  > `review_logs.staff_count` · `jsonb` · opcional
- ☑️ review_total
  > `review_logs.review_total` · `bool` · opcional
- 🔗 staff_id <!-- orbit link: "#staff_items", status: "chave estrangeira" -->
  > `review_logs.staff_id` · `int8` · obrigatória
  >
  > Aponta para [`staff_items`](#staff_items).
- 🔗 report_id <!-- orbit link: "#report_items", status: "chave estrangeira" -->
  > `review_logs.report_id` · `int8` · obrigatória
  >
  > Aponta para [`report_items`](#report_items).
- 🔤 copy_source
  > `review_logs.copy_source` · `varchar` · obrigatória

### license_settings
<!-- orbit
id: "license_settings"
icon: "🗂️"
collapsed: true
-->
> `license_settings` · tabela de Avisos
>
> Uma linha por licença, com 10 colunas e cerca de 325 mil linhas.
>
> **Pertence a** [`ebook_queues`](#ebook_queues)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `license_settings.id` · `int8` · obrigatória
- 🕒 notice_created
  > `license_settings.notice_created` · `timestamp` · opcional
- ☑️ author_created
  > `license_settings.author_created` · `bool` · opcional
- 🔗 vendor_id <!-- orbit link: "#vendor_items", status: "chave estrangeira" -->
  > `license_settings.vendor_id` · `int8` · obrigatória
  >
  > Aponta para [`vendor_items`](#vendor_items).
- 🕒 loan_source
  > `license_settings.loan_source` · `timestamp` · obrigatória
- 🔗 seriess_id <!-- orbit link: "#seriess", status: "chave estrangeira" -->
  > `license_settings.seriess_id` · `int8` · obrigatória
  >
  > Aponta para [`seriess`](#seriess).
- 🕒 notice_updated
  > `license_settings.notice_updated` · `timestamp` · obrigatória
- 🔤 repair_created
  > `license_settings.repair_created` · `varchar` · obrigatória
- 🕒 order_kind
  > `license_settings.order_kind` · `timestamp` · opcional
- 🔗 license_id <!-- orbit link: "#license_logs_2", status: "chave estrangeira" -->
  > `license_settings.license_id` · `int8` · obrigatória
  >
  > Aponta para [`license_logs_2`](#license_logs_2).

## 🌐 Empréstimo digital
<!-- orbit
id: "area-digital-lending"
color: "#059669"
-->
> 10 tabelas. As maiores: [`budgets`](#budgets), [`licenses`](#licenses), [`audit_links_4`](#audit_links_4).
>
> **Relacionada a** [`rooms`](#rooms) · [`license_logs`](#license_logs) · [`budget_settings`](#budget_settings)

### budgets
<!-- orbit
id: "budgets"
icon: "🧮"
collapsed: true
-->
> `budgets` · tabela de Empréstimo digital
>
> Uma linha por orçamento, com 7 colunas e cerca de 227 mil linhas.
>
> **Pertence a** [`card_links_2`](#card_links_2) · [`ebook_logs`](#ebook_logs) · [`series_counts`](#series_counts)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `budgets.id` · `int8` · obrigatória
- ☑️ notice_updated
  > `budgets.notice_updated` · `bool` · obrigatória
- 🕒 author_ended
  > `budgets.author_ended` · `timestamp` · opcional
- 🕒 loan_language
  > `budgets.loan_language` · `timestamp` · opcional
- 🔗 vendor_id <!-- orbit link: "#vendor_counts", status: "chave estrangeira" -->
  > `budgets.vendor_id` · `int8` · obrigatória
  >
  > Aponta para [`vendor_counts`](#vendor_counts).
- ☑️ visit_source
  > `budgets.visit_source` · `bool` · obrigatória
- 🔢 staff_count
  > `budgets.staff_count` · `int4` · obrigatória

### licenses
<!-- orbit
id: "licenses"
icon: "🗃️"
collapsed: true
-->
> `licenses` · tabela de Empréstimo digital
>
> Uma linha por licença, com 19 colunas e cerca de 709 mil linhas.
>
> **Pertence a** [`visits`](#visits) · [`hold_links`](#hold_links)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `licenses.id` · `int8` · obrigatória
- ☑️ order_state
  > `licenses.order_state` · `bool` · obrigatória
- 🔢 review_title
  > `licenses.review_title` · `int4` · obrigatória
- 🔢 hold_kind
  > `licenses.hold_kind` · `int4` · opcional
- ☑️ report_code
  > `licenses.report_code` · `bool` · obrigatória
- 🔗 events_id <!-- orbit link: "#events", status: "chave estrangeira" -->
  > `licenses.events_id` · `int8` · obrigatória
  >
  > Aponta para [`events`](#events).
- 🕒 repair_status
  > `licenses.repair_status` · `timestamp` · opcional
- ☑️ series_started
  > `licenses.series_started` · `bool` · opcional
- 🧩 copy_priority
  > `licenses.copy_priority` · `jsonb` · obrigatória
- 🔗 loan_id <!-- orbit link: "#loan_logs", status: "chave estrangeira" -->
  > `licenses.loan_id` · `int8` · obrigatória
  >
  > Aponta para [`loan_logs`](#loan_logs).
- 🔤 budget_language
  > `licenses.budget_language` · `varchar` · obrigatória
- ☑️ notice_archived
  > `licenses.notice_archived` · `bool` · opcional
- 🕒 budget_archived
  > `licenses.budget_archived` · `timestamp` · obrigatória
- 🔗 budgets_id <!-- orbit link: "#budgets", status: "chave estrangeira" -->
  > `licenses.budgets_id` · `int8` · obrigatória
  >
  > Aponta para [`budgets`](#budgets).
- 🕒 tag_kind
  > `licenses.tag_kind` · `timestamp` · opcional
- 🔤 order_external
  > `licenses.order_external` · `varchar` · obrigatória
- 🧩 role_started
  > `licenses.role_started` · `jsonb` · opcional
- 🔤 event_state
  > `licenses.event_state` · `varchar` · opcional
- 🔢 license_notes
  > `licenses.license_notes` · `int4` · obrigatória

### audit_links_4
<!-- orbit
id: "audit_links_4"
icon: "🧷"
collapsed: true
-->
> `audit_links_4` · tabela de Empréstimo digital
>
> Uma linha por auditoria, com 24 colunas e cerca de 461 mil linhas.
>
> **Pertence a** [`member_counts_2`](#member_counts_2) · [`report_queues`](#report_queues)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `audit_links_4.id` · `int8` · obrigatória
- 🔢 copy_public
  > `audit_links_4.copy_public` · `int4` · opcional
- 🔢 hold_started
  > `audit_links_4.hold_started` · `int4` · opcional
- 🕒 role_title
  > `audit_links_4.role_title` · `timestamp` · obrigatória
- 🧩 series_code
  > `audit_links_4.series_code` · `jsonb` · opcional
- ☑️ role_kind
  > `audit_links_4.role_kind` · `bool` · opcional
- 🧩 visit_updated
  > `audit_links_4.visit_updated` · `jsonb` · obrigatória
- 🔗 series_id <!-- orbit link: "#series_requests", status: "chave estrangeira" -->
  > `audit_links_4.series_id` · `int8` · obrigatória
  >
  > Aponta para [`series_requests`](#series_requests).
- 🧩 tag_code
  > `audit_links_4.tag_code` · `jsonb` · obrigatória
- 🧩 donation_updated
  > `audit_links_4.donation_updated` · `jsonb` · opcional
- 🧩 vendor_external
  > `audit_links_4.vendor_external` · `jsonb` · obrigatória
- 🧩 event_archived
  > `audit_links_4.event_archived` · `jsonb` · opcional
- 🔤 notice_code
  > `audit_links_4.notice_code` · `varchar` · obrigatória
- 🔤 budget_priority
  > `audit_links_4.budget_priority` · `varchar` · opcional
- ☑️ staff_state
  > `audit_links_4.staff_state` · `bool` · obrigatória
- 🔢 donation_code
  > `audit_links_4.donation_code` · `int4` · opcional
- ☑️ notice_kind
  > `audit_links_4.notice_kind` · `bool` · obrigatória
- 🔗 room_id <!-- orbit link: "#room_counts_2", status: "chave estrangeira" -->
  > `audit_links_4.room_id` · `int8` · obrigatória
  >
  > Aponta para [`room_counts_2`](#room_counts_2).
- 🔤 shelf_active
  > `audit_links_4.shelf_active` · `varchar` · obrigatória
- ☑️ order_language
  > `audit_links_4.order_language` · `bool` · obrigatória
- 🕒 ebook_ended
  > `audit_links_4.ebook_ended` · `timestamp` · opcional
- 🔢 staff_amount
  > `audit_links_4.staff_amount` · `int4` · obrigatória
- 🕒 book_language
  > `audit_links_4.book_language` · `timestamp` · opcional
- 🔗 branch_id <!-- orbit link: "#branch_links", status: "chave estrangeira" -->
  > `audit_links_4.branch_id` · `int8` · obrigatória
  >
  > Aponta para [`branch_links`](#branch_links).

### room_counts_2
<!-- orbit
id: "room_counts_2"
icon: "📇"
collapsed: true
-->
> `room_counts_2` · tabela de Empréstimo digital
>
> Uma linha por sala, com 9 colunas e cerca de 509 mil linhas.
>
> **Pertence a** [`report_logs`](#report_logs)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `room_counts_2.id` · `int8` · obrigatória
- 🔗 author_id <!-- orbit link: "#author_links", status: "chave estrangeira" -->
  > `room_counts_2.author_id` · `int8` · obrigatória
  >
  > Aponta para [`author_links`](#author_links).
- 🕒 visit_status
  > `room_counts_2.visit_status` · `timestamp` · opcional
- 🕒 branch_format
  > `room_counts_2.branch_format` · `timestamp` · opcional
- ☑️ series_ended
  > `room_counts_2.series_ended` · `bool` · opcional
- 🔗 loan_id <!-- orbit link: "#loan_settings", status: "chave estrangeira" -->
  > `room_counts_2.loan_id` · `int8` · obrigatória
  >
  > Aponta para [`loan_settings`](#loan_settings).
- 🕒 review_public
  > `room_counts_2.review_public` · `timestamp` · obrigatória
- 🔗 copy_id <!-- orbit link: "#copy_items", status: "chave estrangeira" -->
  > `room_counts_2.copy_id` · `int8` · obrigatória
  >
  > Aponta para [`copy_items`](#copy_items).
- 🔢 device_format
  > `room_counts_2.device_format` · `int4` · opcional

### notice_counts_2
<!-- orbit
id: "notice_counts_2"
icon: "🧷"
collapsed: true
-->
> `notice_counts_2` · tabela de Empréstimo digital
>
> Uma linha por aviso, com 15 colunas e cerca de 588 mil linhas.
>
> **Pertence a** [`tags`](#tags) · [`payment_requests`](#payment_requests)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `notice_counts_2.id` · `int8` · obrigatória
- 🔢 event_priority
  > `notice_counts_2.event_priority` · `int4` · obrigatória
- 🕒 staff_status
  > `notice_counts_2.staff_status` · `timestamp` · opcional
- 🔗 donation_id <!-- orbit link: "#donation_history", status: "chave estrangeira" -->
  > `notice_counts_2.donation_id` · `int8` · obrigatória
  >
  > Aponta para [`donation_history`](#donation_history).
- ☑️ repair_created
  > `notice_counts_2.repair_created` · `bool` · obrigatória
- 🔢 order_label
  > `notice_counts_2.order_label` · `int4` · obrigatória
- 🔤 author_title
  > `notice_counts_2.author_title` · `varchar` · opcional
- 🕒 room_format
  > `notice_counts_2.room_format` · `timestamp` · opcional
- 🧩 review_status
  > `notice_counts_2.review_status` · `jsonb` · opcional
- 🔗 review_id <!-- orbit link: "#review_links", status: "chave estrangeira" -->
  > `notice_counts_2.review_id` · `int8` · obrigatória
  >
  > Aponta para [`review_links`](#review_links).
- ☑️ license_external
  > `notice_counts_2.license_external` · `bool` · opcional
- ☑️ report_region
  > `notice_counts_2.report_region` · `bool` · opcional
- 🔢 hold_code
  > `notice_counts_2.hold_code` · `int4` · obrigatória
- 🔤 vendor_title
  > `notice_counts_2.vendor_title` · `varchar` · opcional
- 🔗 report_id <!-- orbit link: "#report_versions", status: "chave estrangeira" -->
  > `notice_counts_2.report_id` · `int8` · obrigatória
  >
  > Aponta para [`report_versions`](#report_versions).

### room_settings
<!-- orbit
id: "room_settings"
icon: "🧮"
collapsed: true
-->
> `room_settings` · tabela de Empréstimo digital
>
> Uma linha por sala, com 9 colunas e cerca de 519 mil linhas.
>
> **Pertence a** [`report_logs`](#report_logs) · [`author_links`](#author_links) · [`author_history`](#author_history) · [`holds_2`](#holds_2)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `room_settings.id` · `int8` · obrigatória
- 🔢 author_language
  > `room_settings.author_language` · `int4` · obrigatória
- 🔤 book_total
  > `room_settings.book_total` · `varchar` · opcional
- 🧩 license_position
  > `room_settings.license_position` · `jsonb` · obrigatória
- 🔢 repair_source
  > `room_settings.repair_source` · `int4` · obrigatória
- 🕒 hold_priority
  > `room_settings.hold_priority` · `timestamp` · obrigatória
- 🔤 staff_region
  > `room_settings.staff_region` · `varchar` · opcional
- 🔢 order_position
  > `room_settings.order_position` · `int4` · obrigatória
- 🔤 ebook_notes
  > `room_settings.ebook_notes` · `varchar` · opcional

### room_logs
<!-- orbit
id: "room_logs"
icon: "🧷"
collapsed: true
-->
> `room_logs` · tabela de Empréstimo digital
>
> Uma linha por sala, com 20 colunas e cerca de 679 mil linhas.
>
> **Pertence a** [`shelf_queues`](#shelf_queues) · [`audit_links_2`](#audit_links_2) · [`event_queues`](#event_queues)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `room_logs.id` · `int8` · obrigatória
- 🔢 repair_source
  > `room_logs.repair_source` · `int4` · opcional
- 🔗 member_id <!-- orbit link: "#member_history", status: "chave estrangeira" -->
  > `room_logs.member_id` · `int8` · obrigatória
  >
  > Aponta para [`member_history`](#member_history).
- 🔢 audit_archived
  > `room_logs.audit_archived` · `int4` · opcional
- 🔗 copy_id <!-- orbit link: "#copy_history", status: "chave estrangeira" -->
  > `room_logs.copy_id` · `int8` · obrigatória
  >
  > Aponta para [`copy_history`](#copy_history).
- ☑️ author_label
  > `room_logs.author_label` · `bool` · opcional
- 🔗 review_id <!-- orbit link: "#review_logs", status: "chave estrangeira" -->
  > `room_logs.review_id` · `int8` · obrigatória
  >
  > Aponta para [`review_logs`](#review_logs).
- ☑️ shelf_label
  > `room_logs.shelf_label` · `bool` · opcional
- 🕒 budget_reviewed
  > `room_logs.budget_reviewed` · `timestamp` · obrigatória
- 🔢 repair_external
  > `room_logs.repair_external` · `int4` · obrigatória
- 🕒 card_code
  > `room_logs.card_code` · `timestamp` · opcional
- 🔤 book_amount
  > `room_logs.book_amount` · `varchar` · opcional
- ☑️ tag_public
  > `room_logs.tag_public` · `bool` · opcional
- 🕒 report_total
  > `room_logs.report_total` · `timestamp` · obrigatória
- 🧩 payment_status
  > `room_logs.payment_status` · `jsonb` · obrigatória
- 🔗 room_id <!-- orbit link: "#room_counts", status: "chave estrangeira" -->
  > `room_logs.room_id` · `int8` · obrigatória
  >
  > Aponta para [`room_counts`](#room_counts).
- 🔤 order_started
  > `room_logs.order_started` · `varchar` · opcional
- 🔤 donation_updated
  > `room_logs.donation_updated` · `varchar` · obrigatória
- 🔢 notice_ended
  > `room_logs.notice_ended` · `int4` · opcional
- 🔗 visit_id <!-- orbit link: "#visit_items", status: "chave estrangeira" -->
  > `room_logs.visit_id` · `int8` · obrigatória
  >
  > Aponta para [`visit_items`](#visit_items).

### seriess
<!-- orbit
id: "seriess"
icon: "🧮"
collapsed: true
-->
> `seriess` · tabela de Empréstimo digital
>
> Uma linha por série, com 11 colunas e cerca de 228 mil linhas.
>
> **Pertence a** [`author_history`](#author_history) · [`role_counts`](#role_counts) · [`notice_counts_2`](#notice_counts_2)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `seriess.id` · `int8` · obrigatória
- 🔢 notice_kind
  > `seriess.notice_kind` · `int4` · obrigatória
- 🔤 visit_state
  > `seriess.visit_state` · `varchar` · obrigatória
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
  > `seriess.loan_external` · `bool` · obrigatória

### visit_links
<!-- orbit
id: "visit_links"
icon: "🧮"
collapsed: true
-->
> `visit_links` · tabela de Empréstimo digital
>
> Uma linha por visita, com 19 colunas e cerca de 376 mil linhas.
>
> **Pertence a** [`review_logs`](#review_logs) · [`audit_links_2`](#audit_links_2) · [`card_logs`](#card_logs) · [`review_versions`](#review_versions)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `visit_links.id` · `int8` · obrigatória
- 🔢 visit_position
  > `visit_links.visit_position` · `int4` · obrigatória
- 🔤 device_notes
  > `visit_links.device_notes` · `varchar` · opcional
- 🧩 notice_label
  > `visit_links.notice_label` · `jsonb` · opcional
- 🔗 audit_id <!-- orbit link: "#audit_links_3", status: "chave estrangeira" -->
  > `visit_links.audit_id` · `int8` · obrigatória
  >
  > Aponta para [`audit_links_3`](#audit_links_3).
- 🔢 role_name
  > `visit_links.role_name` · `int4` · opcional
- 🕒 card_created
  > `visit_links.card_created` · `timestamp` · opcional
- 🧩 hold_kind
  > `visit_links.hold_kind` · `jsonb` · opcional
- 🔗 loan_id <!-- orbit link: "#loan_logs_2", status: "chave estrangeira" -->
  > `visit_links.loan_id` · `int8` · obrigatória
  >
  > Aponta para [`loan_logs_2`](#loan_logs_2).
- 🧩 shelf_format
  > `visit_links.shelf_format` · `jsonb` · obrigatória
- ☑️ role_source
  > `visit_links.role_source` · `bool` · opcional
- 🧩 repair_code
  > `visit_links.repair_code` · `jsonb` · obrigatória
- 🔤 report_ended
  > `visit_links.report_ended` · `varchar` · opcional
- 🔤 card_format
  > `visit_links.card_format` · `varchar` · opcional
- ☑️ event_name
  > `visit_links.event_name` · `bool` · opcional
- 🕒 room_total
  > `visit_links.room_total` · `timestamp` · obrigatória
- 🔤 event_priority
  > `visit_links.event_priority` · `varchar` · obrigatória
- ☑️ license_archived
  > `visit_links.license_archived` · `bool` · obrigatória
- 🔤 budget_source
  > `visit_links.budget_source` · `varchar` · opcional

### book_items
<!-- orbit
id: "book_items"
icon: "📇"
collapsed: true
-->
> `book_items` · tabela de Empréstimo digital
>
> Uma linha por livro, com 8 colunas e cerca de 399 mil linhas.
>
> **Pertence a** [`author_history`](#author_history) · [`donation_history`](#donation_history)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `book_items.id` · `int8` · obrigatória
- 🕒 staff_active
  > `book_items.staff_active` · `timestamp` · opcional
- 🔗 licenses_id <!-- orbit link: "#licenses", status: "chave estrangeira" -->
  > `book_items.licenses_id` · `int8` · obrigatória
  >
  > Aponta para [`licenses`](#licenses).
- ☑️ fee_ended
  > `book_items.fee_ended` · `bool` · obrigatória
- 🧩 vendor_name
  > `book_items.vendor_name` · `jsonb` · opcional
- 🔤 donation_count
  > `book_items.donation_count` · `varchar` · obrigatória
- 🔗 tag_id <!-- orbit link: "#tag_settings", status: "chave estrangeira" -->
  > `book_items.tag_id` · `int8` · obrigatória
  >
  > Aponta para [`tag_settings`](#tag_settings).
- 🧩 audit_status
  > `book_items.audit_status` · `jsonb` · obrigatória

## 🧰 Manutenção
<!-- orbit
id: "area-maintenance"
color: "#B45309"
-->
> 10 tabelas. As maiores: [`loan_history`](#loan_history), [`book_settings`](#book_settings), [`copy_history`](#copy_history).
>
> **Relacionada a** [`vendor_counts`](#vendor_counts) · [`vendor_history`](#vendor_history) · [`role_counts`](#role_counts)

### loan_history
<!-- orbit
id: "loan_history"
icon: "🧮"
collapsed: true
-->
> `loan_history` · tabela de Manutenção
>
> Uma linha por empréstimo, com 11 colunas e cerca de 728 mil linhas.
>
> **Pertence a** [`author_links_2`](#author_links_2)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `loan_history.id` · `int8` · obrigatória
- 🔗 copy_id <!-- orbit link: "#copy_versions", status: "chave estrangeira" -->
  > `loan_history.copy_id` · `int8` · obrigatória
  >
  > Aponta para [`copy_versions`](#copy_versions).
- 🧩 member_region
  > `loan_history.member_region` · `jsonb` · opcional
- 🕒 budget_title
  > `loan_history.budget_title` · `timestamp` · obrigatória
- ☑️ report_updated
  > `loan_history.report_updated` · `bool` · opcional
- 🔤 author_position
  > `loan_history.author_position` · `varchar` · obrigatória
- 🔢 loan_position
  > `loan_history.loan_position` · `int4` · obrigatória
- 🔢 shelf_created
  > `loan_history.shelf_created` · `int4` · opcional
- 🔤 notice_state
  > `loan_history.notice_state` · `varchar` · opcional
- 🔗 staff_id <!-- orbit link: "#staff_settings", status: "chave estrangeira" -->
  > `loan_history.staff_id` · `int8` · obrigatória
  >
  > Aponta para [`staff_settings`](#staff_settings).
- ☑️ audit_kind
  > `loan_history.audit_kind` · `bool` · opcional

### book_settings
<!-- orbit
id: "book_settings"
icon: "🗃️"
collapsed: true
-->
> `book_settings` · tabela de Manutenção
>
> Uma linha por livro, com 12 colunas e cerca de 113 mil linhas.
>
> **Pertence a** [`audit_links_3`](#audit_links_3) · [`review_queues`](#review_queues)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `book_settings.id` · `int8` · obrigatória
- 🔢 book_source
  > `book_settings.book_source` · `int4` · opcional
- ☑️ report_source
  > `book_settings.report_source` · `bool` · opcional
- 🔗 ebook_id <!-- orbit link: "#ebook_logs", status: "chave estrangeira" -->
  > `book_settings.ebook_id` · `int8` · obrigatória
  >
  > Aponta para [`ebook_logs`](#ebook_logs).
- 🔗 budget_id <!-- orbit link: "#budget_queues", status: "chave estrangeira" -->
  > `book_settings.budget_id` · `int8` · obrigatória
  >
  > Aponta para [`budget_queues`](#budget_queues).
- 🔤 series_format
  > `book_settings.series_format` · `varchar` · obrigatória
- 🧩 shelf_label
  > `book_settings.shelf_label` · `jsonb` · obrigatória
- 🕒 copy_region
  > `book_settings.copy_region` · `timestamp` · obrigatória
- 🧩 repair_ended
  > `book_settings.repair_ended` · `jsonb` · obrigatória
- 🔤 tag_source
  > `book_settings.tag_source` · `varchar` · opcional
- 🔤 ebook_priority
  > `book_settings.ebook_priority` · `varchar` · obrigatória
- 🔢 visit_code
  > `book_settings.visit_code` · `int4` · obrigatória

### copy_history
<!-- orbit
id: "copy_history"
icon: "📇"
collapsed: true
-->
> `copy_history` · tabela de Manutenção
>
> Uma linha por exemplar, com 20 colunas e cerca de 547 mil linhas.
>
> **Pertence a** [`staff_counts`](#staff_counts) · [`series_counts_2`](#series_counts_2) · [`tag_settings`](#tag_settings) · [`budget_settings_2`](#budget_settings_2)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `copy_history.id` · `int8` · obrigatória
- 🔢 hold_total
  > `copy_history.hold_total` · `int4` · opcional
- ☑️ staff_kind
  > `copy_history.staff_kind` · `bool` · obrigatória
- 🔗 device_id <!-- orbit link: "#device_settings", status: "chave estrangeira" -->
  > `copy_history.device_id` · `int8` · obrigatória
  >
  > Aponta para [`device_settings`](#device_settings).
- 🔢 audit_external
  > `copy_history.audit_external` · `int4` · obrigatória
- 🧩 room_status
  > `copy_history.room_status` · `jsonb` · opcional
- 🕒 audit_code
  > `copy_history.audit_code` · `timestamp` · obrigatória
- ☑️ staff_total
  > `copy_history.staff_total` · `bool` · obrigatória
- 🧩 budget_language
  > `copy_history.budget_language` · `jsonb` · opcional
- 🔤 vendor_kind
  > `copy_history.vendor_kind` · `varchar` · obrigatória
- 🔢 role_ended
  > `copy_history.role_ended` · `int4` · opcional
- 🔤 staff_archived
  > `copy_history.staff_archived` · `varchar` · opcional
- 🔗 hold_id <!-- orbit link: "#hold_history", status: "chave estrangeira" -->
  > `copy_history.hold_id` · `int8` · obrigatória
  >
  > Aponta para [`hold_history`](#hold_history).
- 🔗 series_id <!-- orbit link: "#series_requests", status: "chave estrangeira" -->
  > `copy_history.series_id` · `int8` · obrigatória
  >
  > Aponta para [`series_requests`](#series_requests).
- 🔗 series_id_2 <!-- orbit link: "#series_history", status: "chave estrangeira" -->
  > `copy_history.series_id_2` · `int8` · obrigatória
  >
  > Aponta para [`series_history`](#series_history).
- 🔗 license_id <!-- orbit link: "#license_logs_2", status: "chave estrangeira" -->
  > `copy_history.license_id` · `int8` · obrigatória
  >
  > Aponta para [`license_logs_2`](#license_logs_2).
- 🔢 staff_archived_2
  > `copy_history.staff_archived_2` · `int4` · opcional
- 🧩 device_language
  > `copy_history.device_language` · `jsonb` · opcional
- 🔗 donation_id <!-- orbit link: "#donation_requests", status: "chave estrangeira" -->
  > `copy_history.donation_id` · `int8` · obrigatória
  >
  > Aponta para [`donation_requests`](#donation_requests).
- 🕒 device_format
  > `copy_history.device_format` · `timestamp` · opcional

### report_counts
<!-- orbit
id: "report_counts"
icon: "📇"
collapsed: true
-->
> `report_counts` · tabela de Manutenção
>
> Uma linha por relatório, com 24 colunas e cerca de 542 mil linhas.
>
> **Pertence a** [`order_links`](#order_links) · [`loan_history`](#loan_history) · [`review_items`](#review_items) · [`notice_counts_2`](#notice_counts_2)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `report_counts.id` · `int8` · obrigatória
- 🕒 shelf_updated
  > `report_counts.shelf_updated` · `timestamp` · obrigatória
- 🔤 order_started
  > `report_counts.order_started` · `varchar` · opcional
- 🔢 notice_status
  > `report_counts.notice_status` · `int4` · obrigatória
- ☑️ budget_language
  > `report_counts.budget_language` · `bool` · obrigatória
- 🔤 donation_kind
  > `report_counts.donation_kind` · `varchar` · opcional
- 🔗 member_id <!-- orbit link: "#member_versions", status: "chave estrangeira" -->
  > `report_counts.member_id` · `int8` · obrigatória
  >
  > Aponta para [`member_versions`](#member_versions).
- 🔗 donation_id <!-- orbit link: "#donation_queues", status: "chave estrangeira" -->
  > `report_counts.donation_id` · `int8` · obrigatória
  >
  > Aponta para [`donation_queues`](#donation_queues).
- 🔗 room_id <!-- orbit link: "#room_counts_2", status: "chave estrangeira" -->
  > `report_counts.room_id` · `int8` · obrigatória
  >
  > Aponta para [`room_counts_2`](#room_counts_2).
- ☑️ book_code
  > `report_counts.book_code` · `bool` · obrigatória
- 🕒 ebook_started
  > `report_counts.ebook_started` · `timestamp` · obrigatória
- ☑️ visit_ended
  > `report_counts.visit_ended` · `bool` · obrigatória
- 🕒 room_code
  > `report_counts.room_code` · `timestamp` · obrigatória
- 🔗 loan_id <!-- orbit link: "#loan_settings", status: "chave estrangeira" -->
  > `report_counts.loan_id` · `int8` · obrigatória
  >
  > Aponta para [`loan_settings`](#loan_settings).
- 🕒 repair_position
  > `report_counts.repair_position` · `timestamp` · opcional
- 🕒 staff_total
  > `report_counts.staff_total` · `timestamp` · opcional
- 🔤 fee_format
  > `report_counts.fee_format` · `varchar` · obrigatória
- 🔤 repair_title
  > `report_counts.repair_title` · `varchar` · obrigatória
- 🕒 ebook_kind
  > `report_counts.ebook_kind` · `timestamp` · obrigatória
- ☑️ payment_state
  > `report_counts.payment_state` · `bool` · opcional
- ☑️ shelf_public
  > `report_counts.shelf_public` · `bool` · obrigatória
- 🔤 fee_priority
  > `report_counts.fee_priority` · `varchar` · obrigatória
- 🔗 loan_id_2 <!-- orbit link: "#loan_logs_2", status: "chave estrangeira" -->
  > `report_counts.loan_id_2` · `int8` · obrigatória
  >
  > Aponta para [`loan_logs_2`](#loan_logs_2).
- 🔢 staff_kind
  > `report_counts.staff_kind` · `int4` · opcional

### event_queues
<!-- orbit
id: "event_queues"
icon: "🧮"
collapsed: true
-->
> `event_queues` · tabela de Manutenção
>
> Uma linha por evento, com 21 colunas e cerca de 403 mil linhas.
>
> **Pertence a** [`loan_counts`](#loan_counts) · [`license_logs_2`](#license_logs_2) · [`notice_counts`](#notice_counts) · [`donation_links`](#donation_links)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `event_queues.id` · `int8` · obrigatória
- 🔢 role_count
  > `event_queues.role_count` · `int4` · obrigatória
- 🔢 donation_format
  > `event_queues.donation_format` · `int4` · opcional
- 🕒 room_position
  > `event_queues.room_position` · `timestamp` · obrigatória
- ☑️ review_position
  > `event_queues.review_position` · `bool` · obrigatória
- 🧩 budget_count
  > `event_queues.budget_count` · `jsonb` · obrigatória
- 🕒 ebook_updated
  > `event_queues.ebook_updated` · `timestamp` · opcional
- 🔢 notice_label
  > `event_queues.notice_label` · `int4` · obrigatória
- 🕒 review_position_2
  > `event_queues.review_position_2` · `timestamp` · opcional
- 🔢 repair_active
  > `event_queues.repair_active` · `int4` · obrigatória
- 🔢 role_code
  > `event_queues.role_code` · `int4` · opcional
- 🔗 hold_id <!-- orbit link: "#hold_links_2", status: "chave estrangeira" -->
  > `event_queues.hold_id` · `int8` · obrigatória
  >
  > Aponta para [`hold_links_2`](#hold_links_2).
- 🔗 audit_id <!-- orbit link: "#audit_links", status: "chave estrangeira" -->
  > `event_queues.audit_id` · `int8` · obrigatória
  >
  > Aponta para [`audit_links`](#audit_links).
- 🔤 room_archived
  > `event_queues.room_archived` · `varchar` · obrigatória
- ☑️ hold_state
  > `event_queues.hold_state` · `bool` · opcional
- 🔤 review_name
  > `event_queues.review_name` · `varchar` · opcional
- 🔢 visit_source
  > `event_queues.visit_source` · `int4` · opcional
- 🔤 ebook_started
  > `event_queues.ebook_started` · `varchar` · opcional
- 🔢 donation_count
  > `event_queues.donation_count` · `int4` · obrigatória
- ☑️ event_title
  > `event_queues.event_title` · `bool` · obrigatória
- 🔢 audit_region
  > `event_queues.audit_region` · `int4` · opcional

### budget_settings_2
<!-- orbit
id: "budget_settings_2"
icon: "🧷"
collapsed: true
-->
> `budget_settings_2` · tabela de Manutenção
>
> Uma linha por orçamento, com 18 colunas e cerca de 799 mil linhas.
>
> **Pertence a** [`license_versions`](#license_versions) · [`book_links`](#book_links)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `budget_settings_2.id` · `int8` · obrigatória
- ☑️ author_total
  > `budget_settings_2.author_total` · `bool` · opcional
- 🔗 order_id <!-- orbit link: "#order_links", status: "chave estrangeira" -->
  > `budget_settings_2.order_id` · `int8` · obrigatória
  >
  > Aponta para [`order_links`](#order_links).
- 🧩 vendor_source
  > `budget_settings_2.vendor_source` · `jsonb` · obrigatória
- 🕒 device_public
  > `budget_settings_2.device_public` · `timestamp` · opcional
- 🔤 series_priority
  > `budget_settings_2.series_priority` · `varchar` · obrigatória
- 🔢 author_priority
  > `budget_settings_2.author_priority` · `int4` · obrigatória
- 🕒 budget_amount
  > `budget_settings_2.budget_amount` · `timestamp` · obrigatória
- 🕒 visit_label
  > `budget_settings_2.visit_label` · `timestamp` · obrigatória
- 🔗 visit_id <!-- orbit link: "#visit_items", status: "chave estrangeira" -->
  > `budget_settings_2.visit_id` · `int8` · obrigatória
  >
  > Aponta para [`visit_items`](#visit_items).
- 🕒 audit_region
  > `budget_settings_2.audit_region` · `timestamp` · obrigatória
- 🔤 room_total
  > `budget_settings_2.room_total` · `varchar` · opcional
- 🔗 vendor_id <!-- orbit link: "#vendor_settings", status: "chave estrangeira" -->
  > `budget_settings_2.vendor_id` · `int8` · obrigatória
  >
  > Aponta para [`vendor_settings`](#vendor_settings).
- 🧩 notice_position
  > `budget_settings_2.notice_position` · `jsonb` · obrigatória
- 🔢 report_code
  > `budget_settings_2.report_code` · `int4` · obrigatória
- 🧩 repair_language
  > `budget_settings_2.repair_language` · `jsonb` · opcional
- 🔢 series_created
  > `budget_settings_2.series_created` · `int4` · obrigatória
- 🕒 loan_source
  > `budget_settings_2.loan_source` · `timestamp` · obrigatória

### series_counts_2
<!-- orbit
id: "series_counts_2"
icon: "📇"
collapsed: true
-->
> `series_counts_2` · tabela de Manutenção
>
> Uma linha por série, com 11 colunas e cerca de 263 mil linhas.
>
> **Pertence a** [`loan_counts`](#loan_counts) · [`branchs`](#branchs) · [`visit_items`](#visit_items) · [`holds_2`](#holds_2)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `series_counts_2.id` · `int8` · obrigatória
- 🔗 member_id <!-- orbit link: "#member_counts_2", status: "chave estrangeira" -->
  > `series_counts_2.member_id` · `int8` · obrigatória
  >
  > Aponta para [`member_counts_2`](#member_counts_2).
- 🔤 hold_archived
  > `series_counts_2.hold_archived` · `varchar` · obrigatória
- ☑️ notice_position
  > `series_counts_2.notice_position` · `bool` · obrigatória
- 🔤 fee_archived
  > `series_counts_2.fee_archived` · `varchar` · opcional
- 🔤 card_language
  > `series_counts_2.card_language` · `varchar` · opcional
- ☑️ series_title
  > `series_counts_2.series_title` · `bool` · opcional
- 🔗 tag_id <!-- orbit link: "#tag_counts", status: "chave estrangeira" -->
  > `series_counts_2.tag_id` · `int8` · obrigatória
  >
  > Aponta para [`tag_counts`](#tag_counts).
- 🔤 device_format
  > `series_counts_2.device_format` · `varchar` · opcional
- 🧩 hold_source
  > `series_counts_2.hold_source` · `jsonb` · obrigatória
- 🔗 license_id <!-- orbit link: "#license_settings", status: "chave estrangeira" -->
  > `series_counts_2.license_id` · `int8` · obrigatória
  >
  > Aponta para [`license_settings`](#license_settings).

### copy_links
<!-- orbit
id: "copy_links"
icon: "🗃️"
collapsed: true
-->
> `copy_links` · tabela de Manutenção
>
> Uma linha por exemplar, com 16 colunas e cerca de 493 mil linhas.
>
> **Pertence a** [`ebook_requests`](#ebook_requests)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `copy_links.id` · `int8` · obrigatória
- 🧩 book_title
  > `copy_links.book_title` · `jsonb` · obrigatória
- ☑️ audit_status
  > `copy_links.audit_status` · `bool` · opcional
- 🔢 hold_position
  > `copy_links.hold_position` · `int4` · opcional
- 🔢 member_active
  > `copy_links.member_active` · `int4` · opcional
- 🕒 loan_title
  > `copy_links.loan_title` · `timestamp` · obrigatória
- 🕒 branch_notes
  > `copy_links.branch_notes` · `timestamp` · opcional
- 🕒 visit_archived
  > `copy_links.visit_archived` · `timestamp` · opcional
- 🕒 series_updated
  > `copy_links.series_updated` · `timestamp` · opcional
- 🔤 review_archived
  > `copy_links.review_archived` · `varchar` · opcional
- 🕒 notice_updated
  > `copy_links.notice_updated` · `timestamp` · obrigatória
- 🧩 fee_code
  > `copy_links.fee_code` · `jsonb` · opcional
- 🔤 member_state
  > `copy_links.member_state` · `varchar` · opcional
- 🔗 member_id <!-- orbit link: "#member_counts_2", status: "chave estrangeira" -->
  > `copy_links.member_id` · `int8` · obrigatória
  >
  > Aponta para [`member_counts_2`](#member_counts_2).
- 🕒 copy_state
  > `copy_links.copy_state` · `timestamp` · obrigatória
- 🔢 branch_started
  > `copy_links.branch_started` · `int4` · obrigatória

### payment_settings
<!-- orbit
id: "payment_settings"
icon: "🧷"
collapsed: true
-->
> `payment_settings` · tabela de Manutenção
>
> Uma linha por pagamento, com 23 colunas e cerca de 871 mil linhas.
>
> **Pertence a** [`report_links`](#report_links) · [`copy_items`](#copy_items)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `payment_settings.id` · `int8` · obrigatória
- 🔗 ebook_id <!-- orbit link: "#ebook_requests", status: "chave estrangeira" -->
  > `payment_settings.ebook_id` · `int8` · obrigatória
  >
  > Aponta para [`ebook_requests`](#ebook_requests).
- ☑️ notice_reviewed
  > `payment_settings.notice_reviewed` · `bool` · opcional
- 🧩 hold_count
  > `payment_settings.hold_count` · `jsonb` · opcional
- 🧩 hold_format
  > `payment_settings.hold_format` · `jsonb` · opcional
- 🕒 hold_public
  > `payment_settings.hold_public` · `timestamp` · obrigatória
- 🔤 tag_notes
  > `payment_settings.tag_notes` · `varchar` · obrigatória
- 🕒 budget_title
  > `payment_settings.budget_title` · `timestamp` · obrigatória
- 🔤 report_archived
  > `payment_settings.report_archived` · `varchar` · opcional
- 🔢 audit_position
  > `payment_settings.audit_position` · `int4` · opcional
- 🔤 shelf_region
  > `payment_settings.shelf_region` · `varchar` · opcional
- ☑️ vendor_updated
  > `payment_settings.vendor_updated` · `bool` · obrigatória
- 🕒 audit_count
  > `payment_settings.audit_count` · `timestamp` · obrigatória
- 🕒 copy_region
  > `payment_settings.copy_region` · `timestamp` · opcional
- 🧩 card_ended
  > `payment_settings.card_ended` · `jsonb` · opcional
- ☑️ tag_amount
  > `payment_settings.tag_amount` · `bool` · obrigatória
- 🧩 audit_notes
  > `payment_settings.audit_notes` · `jsonb` · opcional
- 🧩 hold_ended
  > `payment_settings.hold_ended` · `jsonb` · obrigatória
- 🧩 branch_code
  > `payment_settings.branch_code` · `jsonb` · opcional
- 🕒 visit_notes
  > `payment_settings.visit_notes` · `timestamp` · opcional
- 🧩 branch_started
  > `payment_settings.branch_started` · `jsonb` · opcional
- 🔗 event_id <!-- orbit link: "#event_history", status: "chave estrangeira" -->
  > `payment_settings.event_id` · `int8` · obrigatória
  >
  > Aponta para [`event_history`](#event_history).
- ☑️ book_position
  > `payment_settings.book_position` · `bool` · opcional

### book_links
<!-- orbit
id: "book_links"
icon: "🗂️"
collapsed: true
-->
> `book_links` · tabela de Manutenção
>
> Uma linha por livro, com 14 colunas e cerca de 223 mil linhas.
>
> **Pertence a** [`budget_queues`](#budget_queues) · [`series_counts`](#series_counts)

- 🔑 id <!-- orbit status: "chave primária" -->
  > `book_links.id` · `int8` · obrigatória
- ☑️ copy_archived
  > `book_links.copy_archived` · `bool` · opcional
- 🔗 event_id <!-- orbit link: "#event_history", status: "chave estrangeira" -->
  > `book_links.event_id` · `int8` · obrigatória
  >
  > Aponta para [`event_history`](#event_history).
- 🧩 payment_amount
  > `book_links.payment_amount` · `jsonb` · opcional
- 🕒 report_title
  > `book_links.report_title` · `timestamp` · obrigatória
- 🧩 copy_name
  > `book_links.copy_name` · `jsonb` · obrigatória
- 🧩 license_public
  > `book_links.license_public` · `jsonb` · opcional
- ☑️ budget_title
  > `book_links.budget_title` · `bool` · obrigatória
- 🔤 event_amount
  > `book_links.event_amount` · `varchar` · opcional
- 🧩 license_position
  > `book_links.license_position` · `jsonb` · obrigatória
- 🧩 repair_priority
  > `book_links.repair_priority` · `jsonb` · obrigatória
- 🔤 room_label
  > `book_links.room_label` · `varchar` · obrigatória
- 🔢 staff_ended
  > `book_links.staff_ended` · `int4` · opcional
- ☑️ role_public
  > `book_links.role_public` · `bool` · opcional

