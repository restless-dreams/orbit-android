---
title: The city library
theme: cosmos
---
# City library
<!-- orbit
id: "library"
-->
> The made-up database of a city library, drawn as a solar system: **124 tables** in **12 areas**. Each planet is an area; fly into one to see its tables, then into a table for its columns.

## 📚 Catalog
<!-- orbit
id: "area-catalog"
color: "#C2304A"
-->
> 10 tables. Largest: [`copy_versions`](#copy_versions), [`fee_history`](#fee_history), [`license_logs`](#license_logs).
>
> **Linked to** [`author_links`](#author_links) · [`member_counts_2`](#member_counts_2) · [`shelfs`](#shelfs)

### copy_versions
<!-- orbit
id: "copy_versions"
icon: "📇"
collapsed: true
-->
> `copy_versions` · table in Catalog
>
> One row per copy, with 18 columns and about 454 thousand rows.
>
> **Belongs to** [`staff_settings`](#staff_settings) · [`card_links_2`](#card_links_2) · [`review_counts`](#review_counts)

- 🔑 id <!-- orbit status: "primary key" -->
  > `copy_versions.id` · `int8` · required
- 🧩 donation_started
  > `copy_versions.donation_started` · `jsonb` · optional
- 🧩 event_format
  > `copy_versions.event_format` · `jsonb` · optional
- 🔢 room_source
  > `copy_versions.room_source` · `int4` · required
- 🧩 license_state
  > `copy_versions.license_state` · `jsonb` · optional
- 🕒 notice_active
  > `copy_versions.notice_active` · `timestamp` · optional
- 🕒 member_archived
  > `copy_versions.member_archived` · `timestamp` · optional
- 🔤 fee_public
  > `copy_versions.fee_public` · `varchar` · optional
- ☑️ fee_region
  > `copy_versions.fee_region` · `bool` · optional
- 🔗 series_id <!-- orbit link: "#series_history", status: "foreign key" -->
  > `copy_versions.series_id` · `int8` · required
  >
  > Points at [`series_history`](#series_history).
- 🔤 staff_position
  > `copy_versions.staff_position` · `varchar` · optional
- 🔢 ebook_created
  > `copy_versions.ebook_created` · `int4` · optional
- 🔗 tag_id <!-- orbit link: "#tag_settings", status: "foreign key" -->
  > `copy_versions.tag_id` · `int8` · required
  >
  > Points at [`tag_settings`](#tag_settings).
- 🧩 member_external
  > `copy_versions.member_external` · `jsonb` · required
- ☑️ event_external
  > `copy_versions.event_external` · `bool` · required
- 🔗 donation_id <!-- orbit link: "#donation_queues", status: "foreign key" -->
  > `copy_versions.donation_id` · `int8` · required
  >
  > Points at [`donation_queues`](#donation_queues).
- ☑️ card_priority
  > `copy_versions.card_priority` · `bool` · required
- ☑️ vendor_notes
  > `copy_versions.vendor_notes` · `bool` · optional

### fee_history
<!-- orbit
id: "fee_history"
icon: "🗃️"
collapsed: true
-->
> `fee_history` · table in Catalog
>
> One row per fee, with 9 columns and about 203 thousand rows.
>
> **Belongs to** [`report_logs`](#report_logs) · [`shelfs`](#shelfs) · [`device_items`](#device_items)

- 🔑 id <!-- orbit status: "primary key" -->
  > `fee_history.id` · `int8` · required
- 🔤 payment_notes
  > `fee_history.payment_notes` · `varchar` · required
- 🔢 ebook_region
  > `fee_history.ebook_region` · `int4` · optional
- ☑️ order_total
  > `fee_history.order_total` · `bool` · required
- 🔤 staff_updated
  > `fee_history.staff_updated` · `varchar` · optional
- 🔗 hold_id <!-- orbit link: "#hold_links", status: "foreign key" -->
  > `fee_history.hold_id` · `int8` · required
  >
  > Points at [`hold_links`](#hold_links).
- 🔗 tags_id <!-- orbit link: "#tags", status: "foreign key" -->
  > `fee_history.tags_id` · `int8` · required
  >
  > Points at [`tags`](#tags).
- 🕒 budget_title
  > `fee_history.budget_title` · `timestamp` · optional
- 🧩 payment_language
  > `fee_history.payment_language` · `jsonb` · optional

### license_logs
<!-- orbit
id: "license_logs"
icon: "🧮"
collapsed: true
-->
> `license_logs` · table in Catalog
>
> One row per license, with 7 columns and about 729 thousand rows.
>
> **Belongs to** [`branch_links`](#branch_links)

- 🔑 id <!-- orbit status: "primary key" -->
  > `license_logs.id` · `int8` · required
- 🔗 ebook_id <!-- orbit link: "#ebook_versions", status: "foreign key" -->
  > `license_logs.ebook_id` · `int8` · required
  >
  > Points at [`ebook_versions`](#ebook_versions).
- 🕒 ebook_count
  > `license_logs.ebook_count` · `timestamp` · optional
- 🕒 ebook_reviewed
  > `license_logs.ebook_reviewed` · `timestamp` · required
- 🔤 budget_external
  > `license_logs.budget_external` · `varchar` · required
- 🔢 role_external
  > `license_logs.role_external` · `int4` · required
- 🔗 hold_id <!-- orbit link: "#hold_logs", status: "foreign key" -->
  > `license_logs.hold_id` · `int8` · required
  >
  > Points at [`hold_logs`](#hold_logs).

### vendor_items
<!-- orbit
id: "vendor_items"
icon: "🗃️"
collapsed: true
-->
> `vendor_items` · table in Catalog
>
> One row per vendor, with 11 columns and about 336 thousand rows.
>
> **Belongs to** [`budget_settings_2`](#budget_settings_2) · [`vendor_counts`](#vendor_counts) · [`card_logs`](#card_logs) · [`fee_requests`](#fee_requests)

- 🔑 id <!-- orbit status: "primary key" -->
  > `vendor_items.id` · `int8` · required
- 🔗 author_id <!-- orbit link: "#author_history", status: "foreign key" -->
  > `vendor_items.author_id` · `int8` · required
  >
  > Points at [`author_history`](#author_history).
- 🔤 card_state
  > `vendor_items.card_state` · `varchar` · optional
- 🔤 notice_region
  > `vendor_items.notice_region` · `varchar` · optional
- 🔗 loan_id <!-- orbit link: "#loan_history", status: "foreign key" -->
  > `vendor_items.loan_id` · `int8` · required
  >
  > Points at [`loan_history`](#loan_history).
- 🧩 license_language
  > `vendor_items.license_language` · `jsonb` · required
- 🕒 donation_external
  > `vendor_items.donation_external` · `timestamp` · required
- 🔗 review_id <!-- orbit link: "#review_versions", status: "foreign key" -->
  > `vendor_items.review_id` · `int8` · required
  >
  > Points at [`review_versions`](#review_versions).
- 🔗 license_id <!-- orbit link: "#license_logs", status: "foreign key" -->
  > `vendor_items.license_id` · `int8` · required
  >
  > Points at [`license_logs`](#license_logs).
- 🕒 member_kind
  > `vendor_items.member_kind` · `timestamp` · required
- 🔤 budget_active
  > `vendor_items.budget_active` · `varchar` · required

### report_logs
<!-- orbit
id: "report_logs"
icon: "🗃️"
collapsed: true
-->
> `report_logs` · table in Catalog
>
> One row per report, with 24 columns and about 452 thousand rows.
>
> **Belongs to** [`hold_links`](#hold_links) · [`visits`](#visits)

- 🔑 id <!-- orbit status: "primary key" -->
  > `report_logs.id` · `int8` · required
- 🧩 review_name
  > `report_logs.review_name` · `jsonb` · optional
- 🧩 repair_status
  > `report_logs.repair_status` · `jsonb` · required
- 🔤 vendor_name
  > `report_logs.vendor_name` · `varchar` · required
- 🧩 loan_code
  > `report_logs.loan_code` · `jsonb` · optional
- 🧩 payment_language
  > `report_logs.payment_language` · `jsonb` · optional
- 🔢 book_status
  > `report_logs.book_status` · `int4` · required
- 🧩 tag_position
  > `report_logs.tag_position` · `jsonb` · optional
- 🕒 repair_count
  > `report_logs.repair_count` · `timestamp` · required
- 🕒 loan_code_2
  > `report_logs.loan_code_2` · `timestamp` · required
- ☑️ book_label
  > `report_logs.book_label` · `bool` · optional
- ☑️ repair_language
  > `report_logs.repair_language` · `bool` · optional
- 🔢 tag_amount
  > `report_logs.tag_amount` · `int4` · optional
- 🔢 member_external
  > `report_logs.member_external` · `int4` · required
- 🔗 review_id <!-- orbit link: "#review_items", status: "foreign key" -->
  > `report_logs.review_id` · `int8` · required
  >
  > Points at [`review_items`](#review_items).
- 🔤 review_status
  > `report_logs.review_status` · `varchar` · optional
- 🔢 report_title
  > `report_logs.report_title` · `int4` · required
- 🔗 holds_id <!-- orbit link: "#holds_2", status: "foreign key" -->
  > `report_logs.holds_id` · `int8` · required
  >
  > Points at [`holds_2`](#holds_2).
- 🔤 author_reviewed
  > `report_logs.author_reviewed` · `varchar` · optional
- 🔤 order_count
  > `report_logs.order_count` · `varchar` · required
- 🧩 series_public
  > `report_logs.series_public` · `jsonb` · required
- ☑️ visit_language
  > `report_logs.visit_language` · `bool` · required
- 🕒 staff_reviewed
  > `report_logs.staff_reviewed` · `timestamp` · optional
- 🕒 role_total
  > `report_logs.role_total` · `timestamp` · required

### visit_items
<!-- orbit
id: "visit_items"
icon: "📇"
collapsed: true
-->
> `visit_items` · table in Catalog
>
> One row per visit, with 7 columns and about 234 thousand rows.
>
> **Belongs to** [`donations`](#donations) · [`holds`](#holds) · [`visits`](#visits) · [`shelf_counts`](#shelf_counts)

- 🔑 id <!-- orbit status: "primary key" -->
  > `visit_items.id` · `int8` · required
- 🔢 fee_region
  > `visit_items.fee_region` · `int4` · optional
- ☑️ device_total
  > `visit_items.device_total` · `bool` · optional
- 🔗 notice_id <!-- orbit link: "#notice_counts", status: "foreign key" -->
  > `visit_items.notice_id` · `int8` · required
  >
  > Points at [`notice_counts`](#notice_counts).
- 🕒 event_ended
  > `visit_items.event_ended` · `timestamp` · optional
- 🔤 license_status
  > `visit_items.license_status` · `varchar` · optional
- ☑️ room_created
  > `visit_items.room_created` · `bool` · optional

### author_links
<!-- orbit
id: "author_links"
icon: "🧮"
collapsed: true
-->
> `author_links` · table in Catalog
>
> One row per author, with 19 columns and about 399 thousand rows.
>
> **Belongs to** [`report_queues_2`](#report_queues_2) · [`staff_counts`](#staff_counts) · [`audit_queues`](#audit_queues)

- 🔑 id <!-- orbit status: "primary key" -->
  > `author_links.id` · `int8` · required
- 🔗 staff_id <!-- orbit link: "#staff_counts", status: "foreign key" -->
  > `author_links.staff_id` · `int8` · required
  >
  > Points at [`staff_counts`](#staff_counts).
- ☑️ license_state
  > `author_links.license_state` · `bool` · required
- 🕒 budget_code
  > `author_links.budget_code` · `timestamp` · optional
- 🔗 series_id <!-- orbit link: "#series_links", status: "foreign key" -->
  > `author_links.series_id` · `int8` · required
  >
  > Points at [`series_links`](#series_links).
- 🔤 tag_total
  > `author_links.tag_total` · `varchar` · optional
- 🔢 staff_state
  > `author_links.staff_state` · `int4` · required
- ☑️ report_format
  > `author_links.report_format` · `bool` · required
- 🔗 audit_id <!-- orbit link: "#audit_queues", status: "foreign key" -->
  > `author_links.audit_id` · `int8` · required
  >
  > Points at [`audit_queues`](#audit_queues).
- 🔗 copy_id <!-- orbit link: "#copy_items", status: "foreign key" -->
  > `author_links.copy_id` · `int8` · required
  >
  > Points at [`copy_items`](#copy_items).
- 🔢 book_created
  > `author_links.book_created` · `int4` · optional
- ☑️ audit_name
  > `author_links.audit_name` · `bool` · optional
- 🔤 visit_public
  > `author_links.visit_public` · `varchar` · required
- 🔗 payment_id <!-- orbit link: "#payment_settings", status: "foreign key" -->
  > `author_links.payment_id` · `int8` · required
  >
  > Points at [`payment_settings`](#payment_settings).
- 🕒 license_archived
  > `author_links.license_archived` · `timestamp` · required
- 🧩 notice_amount
  > `author_links.notice_amount` · `jsonb` · optional
- 🕒 notice_title
  > `author_links.notice_title` · `timestamp` · optional
- 🧩 donation_source
  > `author_links.donation_source` · `jsonb` · optional
- 🧩 payment_position
  > `author_links.payment_position` · `jsonb` · required

### donation_links
<!-- orbit
id: "donation_links"
icon: "🧮"
collapsed: true
-->
> `donation_links` · table in Catalog
>
> One row per donation, with 17 columns and about 146 thousand rows.
>
> **Belongs to** [`report_logs_2`](#report_logs_2) · [`payment_versions`](#payment_versions) · [`book_settings`](#book_settings)

- 🔑 id <!-- orbit status: "primary key" -->
  > `donation_links.id` · `int8` · required
- ☑️ member_total
  > `donation_links.member_total` · `bool` · optional
- ☑️ audit_archived
  > `donation_links.audit_archived` · `bool` · optional
- 🕒 payment_archived
  > `donation_links.payment_archived` · `timestamp` · optional
- 🔢 loan_source
  > `donation_links.loan_source` · `int4` · optional
- ☑️ repair_region
  > `donation_links.repair_region` · `bool` · required
- 🔤 payment_created
  > `donation_links.payment_created` · `varchar` · required
- 🔢 role_notes
  > `donation_links.role_notes` · `int4` · required
- ☑️ member_ended
  > `donation_links.member_ended` · `bool` · optional
- 🔗 notice_id <!-- orbit link: "#notice_counts", status: "foreign key" -->
  > `donation_links.notice_id` · `int8` · required
  >
  > Points at [`notice_counts`](#notice_counts).
- ☑️ device_label
  > `donation_links.device_label` · `bool` · required
- 🕒 member_total_2
  > `donation_links.member_total_2` · `timestamp` · required
- 🕒 visit_started
  > `donation_links.visit_started` · `timestamp` · optional
- 🔤 report_archived
  > `donation_links.report_archived` · `varchar` · required
- 🧩 staff_label
  > `donation_links.staff_label` · `jsonb` · optional
- 🧩 donation_priority
  > `donation_links.donation_priority` · `jsonb` · optional
- 🔗 tag_id <!-- orbit link: "#tag_requests", status: "foreign key" -->
  > `donation_links.tag_id` · `int8` · required
  >
  > Points at [`tag_requests`](#tag_requests).

### budget_settings
<!-- orbit
id: "budget_settings"
icon: "📇"
collapsed: true
-->
> `budget_settings` · table in Catalog
>
> One row per budget, with 23 columns and about 153 thousand rows.
>
> **Belongs to** [`review_items`](#review_items) · [`visit_items`](#visit_items) · [`loan_logs_2`](#loan_logs_2) · [`card_queues`](#card_queues)

- 🔑 id <!-- orbit status: "primary key" -->
  > `budget_settings.id` · `int8` · required
- ☑️ card_language
  > `budget_settings.card_language` · `bool` · required
- ☑️ review_reviewed
  > `budget_settings.review_reviewed` · `bool` · optional
- 🔤 fee_external
  > `budget_settings.fee_external` · `varchar` · optional
- 🔤 event_reviewed
  > `budget_settings.event_reviewed` · `varchar` · required
- ☑️ donation_state
  > `budget_settings.donation_state` · `bool` · optional
- 🔤 device_label
  > `budget_settings.device_label` · `varchar` · required
- 🔗 shelf_id <!-- orbit link: "#shelf_queues", status: "foreign key" -->
  > `budget_settings.shelf_id` · `int8` · required
  >
  > Points at [`shelf_queues`](#shelf_queues).
- 🕒 vendor_started
  > `budget_settings.vendor_started` · `timestamp` · required
- 🕒 review_amount
  > `budget_settings.review_amount` · `timestamp` · required
- 🔢 vendor_active
  > `budget_settings.vendor_active` · `int4` · required
- ☑️ budget_started
  > `budget_settings.budget_started` · `bool` · optional
- 🧩 role_format
  > `budget_settings.role_format` · `jsonb` · optional
- 🔗 license_id <!-- orbit link: "#license_logs", status: "foreign key" -->
  > `budget_settings.license_id` · `int8` · required
  >
  > Points at [`license_logs`](#license_logs).
- 🧩 notice_source
  > `budget_settings.notice_source` · `jsonb` · optional
- 🔗 budget_id <!-- orbit link: "#budget_settings_2", status: "foreign key" -->
  > `budget_settings.budget_id` · `int8` · required
  >
  > Points at [`budget_settings_2`](#budget_settings_2).
- 🧩 budget_code
  > `budget_settings.budget_code` · `jsonb` · required
- 🔢 device_started
  > `budget_settings.device_started` · `int4` · optional
- 🔢 vendor_updated
  > `budget_settings.vendor_updated` · `int4` · optional
- 🕒 device_started_2
  > `budget_settings.device_started_2` · `timestamp` · required
- 🔤 audit_started
  > `budget_settings.audit_started` · `varchar` · required
- 🧩 audit_source
  > `budget_settings.audit_source` · `jsonb` · required
- 🔗 shelf_id_2 <!-- orbit link: "#shelf_counts", status: "foreign key" -->
  > `budget_settings.shelf_id_2` · `int8` · required
  >
  > Points at [`shelf_counts`](#shelf_counts).

### copy_items
<!-- orbit
id: "copy_items"
icon: "🧮"
collapsed: true
-->
> `copy_items` · table in Catalog
>
> One row per copy, with 11 columns and about 367 thousand rows.
>
> **Belongs to** [`review_queues`](#review_queues)

- 🔑 id <!-- orbit status: "primary key" -->
  > `copy_items.id` · `int8` · required
- 🧩 payment_reviewed
  > `copy_items.payment_reviewed` · `jsonb` · optional
- 🔗 payment_id <!-- orbit link: "#payment_versions", status: "foreign key" -->
  > `copy_items.payment_id` · `int8` · required
  >
  > Points at [`payment_versions`](#payment_versions).
- 🔤 copy_format
  > `copy_items.copy_format` · `varchar` · required
- 🕒 hold_updated
  > `copy_items.hold_updated` · `timestamp` · required
- 🧩 fee_kind
  > `copy_items.fee_kind` · `jsonb` · required
- ☑️ shelf_kind
  > `copy_items.shelf_kind` · `bool` · required
- 🔗 card_id <!-- orbit link: "#card_links_2", status: "foreign key" -->
  > `copy_items.card_id` · `int8` · required
  >
  > Points at [`card_links_2`](#card_links_2).
- 🧩 event_language
  > `copy_items.event_language` · `jsonb` · optional
- 🕒 shelf_updated
  > `copy_items.shelf_updated` · `timestamp` · required
- 🔗 order_id <!-- orbit link: "#order_items", status: "foreign key" -->
  > `copy_items.order_id` · `int8` · required
  >
  > Points at [`order_items`](#order_items).

## 🧑‍🤝‍🧑 Members
<!-- orbit
id: "area-members"
color: "#2F7CF6"
-->
> 9 tables. Largest: [`member_counts`](#member_counts), [`order_versions`](#order_versions), [`series_requests`](#series_requests).
>
> **Linked to** [`holds`](#holds) · [`audit_settings`](#audit_settings) · [`room_requests`](#room_requests)

### member_counts
<!-- orbit
id: "member_counts"
icon: "📇"
collapsed: true
-->
> `member_counts` · table in Members
>
> One row per member, with 80 columns and about 175 thousand rows.
>
> **Belongs to** [`ebook_requests`](#ebook_requests)

- 🔑 id <!-- orbit status: "primary key" -->
  > `member_counts.id` · `int8` · required
- ☑️ series_active
  > `member_counts.series_active` · `bool` · optional
- 🧩 branch_amount
  > `member_counts.branch_amount` · `jsonb` · required
- 🔤 visit_updated
  > `member_counts.visit_updated` · `varchar` · optional
- ☑️ book_priority
  > `member_counts.book_priority` · `bool` · optional
- 🔤 role_archived
  > `member_counts.role_archived` · `varchar` · required
- 🔤 hold_position
  > `member_counts.hold_position` · `varchar` · optional
- 🧩 branch_ended
  > `member_counts.branch_ended` · `jsonb` · optional
- 🔢 event_state
  > `member_counts.event_state` · `int4` · optional
- ☑️ donation_active
  > `member_counts.donation_active` · `bool` · optional
- ☑️ card_active
  > `member_counts.card_active` · `bool` · optional
- 🧩 repair_public
  > `member_counts.repair_public` · `jsonb` · optional
- 🔗 payment_id <!-- orbit link: "#payment_settings", status: "foreign key" -->
  > `member_counts.payment_id` · `int8` · required
  >
  > Points at [`payment_settings`](#payment_settings).
- ☑️ order_public
  > `member_counts.order_public` · `bool` · required
- 🔤 event_label
  > `member_counts.event_label` · `varchar` · optional
- 🕒 ebook_reviewed
  > `member_counts.ebook_reviewed` · `timestamp` · required
- 🔢 book_label
  > `member_counts.book_label` · `int4` · required
- 🔗 budget_id <!-- orbit link: "#budget_queues", status: "foreign key" -->
  > `member_counts.budget_id` · `int8` · required
  >
  > Points at [`budget_queues`](#budget_queues).
- ☑️ branch_code
  > `member_counts.branch_code` · `bool` · optional
- ☑️ report_label
  > `member_counts.report_label` · `bool` · required
- 🔤 repair_state
  > `member_counts.repair_state` · `varchar` · required
- 🔗 member_id <!-- orbit link: "#member_counts_2", status: "foreign key" -->
  > `member_counts.member_id` · `int8` · required
  >
  > Points at [`member_counts_2`](#member_counts_2).
- 🔢 tag_language
  > `member_counts.tag_language` · `int4` · optional
- ☑️ staff_count
  > `member_counts.staff_count` · `bool` · optional
- 🔤 fee_created
  > `member_counts.fee_created` · `varchar` · required
- 🕒 role_updated
  > `member_counts.role_updated` · `timestamp` · required
- 🔗 tag_id <!-- orbit link: "#tag_counts", status: "foreign key" -->
  > `member_counts.tag_id` · `int8` · required
  >
  > Points at [`tag_counts`](#tag_counts).
- 🔢 loan_external
  > `member_counts.loan_external` · `int4` · optional
- 🔤 copy_title
  > `member_counts.copy_title` · `varchar` · required
- 🔗 order_id <!-- orbit link: "#order_logs", status: "foreign key" -->
  > `member_counts.order_id` · `int8` · required
  >
  > Points at [`order_logs`](#order_logs).
- 🕒 donation_code
  > `member_counts.donation_code` · `timestamp` · required
- 🔢 copy_archived
  > `member_counts.copy_archived` · `int4` · required
- 🔢 device_notes
  > `member_counts.device_notes` · `int4` · required
- 🔢 shelf_public
  > `member_counts.shelf_public` · `int4` · required
- 🕒 notice_amount
  > `member_counts.notice_amount` · `timestamp` · required
- 🔤 license_position
  > `member_counts.license_position` · `varchar` · required
- 🕒 ebook_notes
  > `member_counts.ebook_notes` · `timestamp` · optional
- 🕒 branch_reviewed
  > `member_counts.branch_reviewed` · `timestamp` · required
- ☑️ budget_active
  > `member_counts.budget_active` · `bool` · required
- ☑️ series_active_2
  > `member_counts.series_active_2` · `bool` · optional
- 🔢 notice_name
  > `member_counts.notice_name` · `int4` · required
- 🔢 hold_started
  > `member_counts.hold_started` · `int4` · required
- 🔗 report_id <!-- orbit link: "#report_counts", status: "foreign key" -->
  > `member_counts.report_id` · `int8` · required
  >
  > Points at [`report_counts`](#report_counts).
- 🧩 hold_public
  > `member_counts.hold_public` · `jsonb` · optional
- 🔗 card_id <!-- orbit link: "#card_queues", status: "foreign key" -->
  > `member_counts.card_id` · `int8` · required
  >
  > Points at [`card_queues`](#card_queues).
- ☑️ series_format
  > `member_counts.series_format` · `bool` · optional
- 🔗 staff_id <!-- orbit link: "#staff_logs", status: "foreign key" -->
  > `member_counts.staff_id` · `int8` · required
  >
  > Points at [`staff_logs`](#staff_logs).
- 🔢 author_public
  > `member_counts.author_public` · `int4` · required
- 🔢 budget_status
  > `member_counts.budget_status` · `int4` · required
- ☑️ room_source
  > `member_counts.room_source` · `bool` · optional
- 🔢 shelf_language
  > `member_counts.shelf_language` · `int4` · required
- 🔢 shelf_kind
  > `member_counts.shelf_kind` · `int4` · optional
- 🔤 donation_format
  > `member_counts.donation_format` · `varchar` · optional
- 🔢 shelf_updated
  > `member_counts.shelf_updated` · `int4` · required
- ☑️ audit_title
  > `member_counts.audit_title` · `bool` · optional
- 🧩 member_ended
  > `member_counts.member_ended` · `jsonb` · required
- 🔗 vendor_id <!-- orbit link: "#vendor_history", status: "foreign key" -->
  > `member_counts.vendor_id` · `int8` · required
  >
  > Points at [`vendor_history`](#vendor_history).
- 🕒 fee_reviewed
  > `member_counts.fee_reviewed` · `timestamp` · optional
- 🔢 member_active
  > `member_counts.member_active` · `int4` · required
- 🧩 budget_source
  > `member_counts.budget_source` · `jsonb` · optional
- ☑️ license_priority
  > `member_counts.license_priority` · `bool` · required
- 🔗 payment_id_2 <!-- orbit link: "#payment_requests", status: "foreign key" -->
  > `member_counts.payment_id_2` · `int8` · required
  >
  > Points at [`payment_requests`](#payment_requests).
- 🕒 event_priority
  > `member_counts.event_priority` · `timestamp` · required
- 🔗 author_id <!-- orbit link: "#author_links", status: "foreign key" -->
  > `member_counts.author_id` · `int8` · required
  >
  > Points at [`author_links`](#author_links).
- 🔗 copy_id <!-- orbit link: "#copy_counts", status: "foreign key" -->
  > `member_counts.copy_id` · `int8` · required
  >
  > Points at [`copy_counts`](#copy_counts).
- 🔤 staff_code
  > `member_counts.staff_code` · `varchar` · optional
- 🕒 card_code
  > `member_counts.card_code` · `timestamp` · optional
- 🔢 role_notes
  > `member_counts.role_notes` · `int4` · optional
- 🧩 member_public
  > `member_counts.member_public` · `jsonb` · optional
- 🔗 loan_id <!-- orbit link: "#loan_history", status: "foreign key" -->
  > `member_counts.loan_id` · `int8` · required
  >
  > Points at [`loan_history`](#loan_history).
- 🕒 payment_status
  > `member_counts.payment_status` · `timestamp` · required
- 🔤 branch_started
  > `member_counts.branch_started` · `varchar` · required
- ☑️ staff_state
  > `member_counts.staff_state` · `bool` · optional
- ☑️ visit_label
  > `member_counts.visit_label` · `bool` · optional
- 🧩 loan_created
  > `member_counts.loan_created` · `jsonb` · required
- 🧩 room_status
  > `member_counts.room_status` · `jsonb` · required
- 🕒 role_language
  > `member_counts.role_language` · `timestamp` · required
- 🔗 fee_id <!-- orbit link: "#fee_requests", status: "foreign key" -->
  > `member_counts.fee_id` · `int8` · required
  >
  > Points at [`fee_requests`](#fee_requests).
- 🕒 visit_count
  > `member_counts.visit_count` · `timestamp` · optional
- ☑️ loan_state
  > `member_counts.loan_state` · `bool` · required

### order_versions
<!-- orbit
id: "order_versions"
icon: "🧷"
collapsed: true
-->
> `order_versions` · table in Members
>
> One row per order, with 18 columns and about 804 thousand rows.
>
> **Belongs to** [`device_items`](#device_items) · [`audit_queues`](#audit_queues) · [`report_items`](#report_items)

- 🔑 id <!-- orbit status: "primary key" -->
  > `order_versions.id` · `int8` · required
- 🕒 shelf_status
  > `order_versions.shelf_status` · `timestamp` · optional
- 🔢 member_created
  > `order_versions.member_created` · `int4` · optional
- 🔤 series_public
  > `order_versions.series_public` · `varchar` · required
- 🧩 member_kind
  > `order_versions.member_kind` · `jsonb` · required
- 🔢 copy_reviewed
  > `order_versions.copy_reviewed` · `int4` · required
- 🔗 series_id <!-- orbit link: "#series_counts", status: "foreign key" -->
  > `order_versions.series_id` · `int8` · required
  >
  > Points at [`series_counts`](#series_counts).
- 🔢 role_started
  > `order_versions.role_started` · `int4` · optional
- 🔤 copy_external
  > `order_versions.copy_external` · `varchar` · required
- 🔗 donation_id <!-- orbit link: "#donation_history", status: "foreign key" -->
  > `order_versions.donation_id` · `int8` · required
  >
  > Points at [`donation_history`](#donation_history).
- ☑️ hold_created
  > `order_versions.hold_created` · `bool` · required
- 🧩 notice_created
  > `order_versions.notice_created` · `jsonb` · optional
- 🔤 device_code
  > `order_versions.device_code` · `varchar` · required
- 🔢 license_updated
  > `order_versions.license_updated` · `int4` · required
- 🔗 loan_id <!-- orbit link: "#loan_logs", status: "foreign key" -->
  > `order_versions.loan_id` · `int8` · required
  >
  > Points at [`loan_logs`](#loan_logs).
- 🔗 staff_id <!-- orbit link: "#staff_counts", status: "foreign key" -->
  > `order_versions.staff_id` · `int8` · required
  >
  > Points at [`staff_counts`](#staff_counts).
- 🕒 audit_source
  > `order_versions.audit_source` · `timestamp` · optional
- 🕒 notice_created_2
  > `order_versions.notice_created_2` · `timestamp` · optional

### series_requests
<!-- orbit
id: "series_requests"
icon: "🗂️"
collapsed: true
-->
> `series_requests` · table in Members
>
> One row per series, with 6 columns and about 472 thousand rows.
>
> **Belongs to** [`device_settings`](#device_settings) · [`ebook_logs`](#ebook_logs)

- 🔑 id <!-- orbit status: "primary key" -->
  > `series_requests.id` · `int8` · required
- 🔢 book_started
  > `series_requests.book_started` · `int4` · required
- 🔢 report_updated
  > `series_requests.report_updated` · `int4` · optional
- 🔤 ebook_count
  > `series_requests.ebook_count` · `varchar` · required
- 🔢 visit_code
  > `series_requests.visit_code` · `int4` · required
- 🔗 visit_id <!-- orbit link: "#visit_items", status: "foreign key" -->
  > `series_requests.visit_id` · `int8` · required
  >
  > Points at [`visit_items`](#visit_items).

### donation_requests
<!-- orbit
id: "donation_requests"
icon: "🧷"
collapsed: true
-->
> `donation_requests` · table in Members
>
> One row per donation, with 7 columns and about 764 thousand rows.
>
> **Belongs to** [`budgets`](#budgets) · [`review_items`](#review_items)

- 🔑 id <!-- orbit status: "primary key" -->
  > `donation_requests.id` · `int8` · required
- 🧩 hold_state
  > `donation_requests.hold_state` · `jsonb` · optional
- 🔗 report_id <!-- orbit link: "#report_counts", status: "foreign key" -->
  > `donation_requests.report_id` · `int8` · required
  >
  > Points at [`report_counts`](#report_counts).
- 🧩 loan_updated
  > `donation_requests.loan_updated` · `jsonb` · required
- 🕒 ebook_position
  > `donation_requests.ebook_position` · `timestamp` · optional
- 🔢 device_started
  > `donation_requests.device_started` · `int4` · required
- ☑️ budget_amount
  > `donation_requests.budget_amount` · `bool` · required

### events
<!-- orbit
id: "events"
icon: "📇"
collapsed: true
-->
> `events` · table in Members
>
> One row per events, with 18 columns and about 464 thousand rows.
>
> **Belongs to** [`card_queues`](#card_queues) · [`report_versions`](#report_versions) · [`order_links`](#order_links)

- 🔑 id <!-- orbit status: "primary key" -->
  > `events.id` · `int8` · required
- 🔢 report_code
  > `events.report_code` · `int4` · required
- 🕒 ebook_updated
  > `events.ebook_updated` · `timestamp` · required
- 🕒 fee_external
  > `events.fee_external` · `timestamp` · optional
- 🔢 device_notes
  > `events.device_notes` · `int4` · optional
- 🧩 visit_total
  > `events.visit_total` · `jsonb` · required
- 🔗 book_id <!-- orbit link: "#book_links", status: "foreign key" -->
  > `events.book_id` · `int8` · required
  >
  > Points at [`book_links`](#book_links).
- 🔢 hold_total
  > `events.hold_total` · `int4` · required
- 🔢 card_started
  > `events.card_started` · `int4` · optional
- 🔤 order_position
  > `events.order_position` · `varchar` · required
- 🕒 shelf_archived
  > `events.shelf_archived` · `timestamp` · optional
- 🔢 shelf_priority
  > `events.shelf_priority` · `int4` · required
- 🧩 loan_ended
  > `events.loan_ended` · `jsonb` · required
- 🔗 ebook_id <!-- orbit link: "#ebook_versions", status: "foreign key" -->
  > `events.ebook_id` · `int8` · required
  >
  > Points at [`ebook_versions`](#ebook_versions).
- ☑️ fee_archived
  > `events.fee_archived` · `bool` · optional
- 🔤 license_total
  > `events.license_total` · `varchar` · optional
- 🧩 notice_active
  > `events.notice_active` · `jsonb` · optional
- 🧩 author_total
  > `events.author_total` · `jsonb` · required

### report_queues
<!-- orbit
id: "report_queues"
icon: "🗂️"
collapsed: true
-->
> `report_queues` · table in Members
>
> One row per report, with 17 columns and about 584 thousand rows.
>
> **Belongs to** [`holds_2`](#holds_2) · [`loan_counts`](#loan_counts)

- 🔑 id <!-- orbit status: "primary key" -->
  > `report_queues.id` · `int8` · required
- 🔢 repair_notes
  > `report_queues.repair_notes` · `int4` · required
- 🧩 notice_reviewed
  > `report_queues.notice_reviewed` · `jsonb` · optional
- 🔗 holds_id <!-- orbit link: "#holds", status: "foreign key" -->
  > `report_queues.holds_id` · `int8` · required
  >
  > Points at [`holds`](#holds).
- 🧩 report_language
  > `report_queues.report_language` · `jsonb` · required
- 🔗 author_id <!-- orbit link: "#author_links_3", status: "foreign key" -->
  > `report_queues.author_id` · `int8` · required
  >
  > Points at [`author_links_3`](#author_links_3).
- 🧩 notice_code
  > `report_queues.notice_code` · `jsonb` · required
- 🕒 report_status
  > `report_queues.report_status` · `timestamp` · optional
- 🔢 member_status
  > `report_queues.member_status` · `int4` · required
- 🔢 tag_name
  > `report_queues.tag_name` · `int4` · optional
- 🔢 copy_public
  > `report_queues.copy_public` · `int4` · optional
- 🔢 copy_reviewed
  > `report_queues.copy_reviewed` · `int4` · optional
- ☑️ audit_name
  > `report_queues.audit_name` · `bool` · optional
- ☑️ hold_label
  > `report_queues.hold_label` · `bool` · required
- 🕒 card_priority
  > `report_queues.card_priority` · `timestamp` · optional
- 🕒 copy_created
  > `report_queues.copy_created` · `timestamp` · optional
- 🕒 room_name
  > `report_queues.room_name` · `timestamp` · optional

### card_links
<!-- orbit
id: "card_links"
icon: "🗂️"
collapsed: true
-->
> `card_links` · table in Members
>
> One row per card, with 17 columns and about 262 thousand rows.
>
> **Belongs to** [`notice_requests`](#notice_requests) · [`loan_logs`](#loan_logs)

- 🔑 id <!-- orbit status: "primary key" -->
  > `card_links.id` · `int8` · required
- 🕒 branch_name
  > `card_links.branch_name` · `timestamp` · required
- 🔤 ebook_label
  > `card_links.ebook_label` · `varchar` · required
- 🔗 license_id <!-- orbit link: "#license_queues", status: "foreign key" -->
  > `card_links.license_id` · `int8` · required
  >
  > Points at [`license_queues`](#license_queues).
- ☑️ shelf_created
  > `card_links.shelf_created` · `bool` · optional
- 🧩 card_status
  > `card_links.card_status` · `jsonb` · required
- 🔗 budget_id <!-- orbit link: "#budget_settings", status: "foreign key" -->
  > `card_links.budget_id` · `int8` · required
  >
  > Points at [`budget_settings`](#budget_settings).
- ☑️ review_position
  > `card_links.review_position` · `bool` · required
- 🧩 donation_source
  > `card_links.donation_source` · `jsonb` · optional
- 🔤 series_ended
  > `card_links.series_ended` · `varchar` · optional
- 🔗 donation_id <!-- orbit link: "#donation_history", status: "foreign key" -->
  > `card_links.donation_id` · `int8` · required
  >
  > Points at [`donation_history`](#donation_history).
- 🔢 hold_count
  > `card_links.hold_count` · `int4` · required
- 🔤 loan_total
  > `card_links.loan_total` · `varchar` · required
- 🔗 order_id <!-- orbit link: "#order_items", status: "foreign key" -->
  > `card_links.order_id` · `int8` · required
  >
  > Points at [`order_items`](#order_items).
- 🔤 room_ended
  > `card_links.room_ended` · `varchar` · optional
- 🕒 device_created
  > `card_links.device_created` · `timestamp` · required
- 🔢 order_count
  > `card_links.order_count` · `int4` · required

### budget_queues
<!-- orbit
id: "budget_queues"
icon: "📇"
collapsed: true
-->
> `budget_queues` · table in Members
>
> One row per budget, with 16 columns and about 189 thousand rows.
>
> **Belongs to** [`license_queues_2`](#license_queues_2) · [`loan_logs_2`](#loan_logs_2)

- 🔑 id <!-- orbit status: "primary key" -->
  > `budget_queues.id` · `int8` · required
- 🔗 copy_id <!-- orbit link: "#copy_links", status: "foreign key" -->
  > `budget_queues.copy_id` · `int8` · required
  >
  > Points at [`copy_links`](#copy_links).
- ☑️ member_archived
  > `budget_queues.member_archived` · `bool` · optional
- 🧩 room_priority
  > `budget_queues.room_priority` · `jsonb` · required
- 🧩 tag_priority
  > `budget_queues.tag_priority` · `jsonb` · required
- 🔗 payment_id <!-- orbit link: "#payment_settings", status: "foreign key" -->
  > `budget_queues.payment_id` · `int8` · required
  >
  > Points at [`payment_settings`](#payment_settings).
- 🔢 repair_notes
  > `budget_queues.repair_notes` · `int4` · required
- 🧩 book_archived
  > `budget_queues.book_archived` · `jsonb` · optional
- 🔗 review_id <!-- orbit link: "#review_counts", status: "foreign key" -->
  > `budget_queues.review_id` · `int8` · required
  >
  > Points at [`review_counts`](#review_counts).
- 🔢 budget_language
  > `budget_queues.budget_language` · `int4` · required
- 🧩 payment_reviewed
  > `budget_queues.payment_reviewed` · `jsonb` · optional
- ☑️ vendor_created
  > `budget_queues.vendor_created` · `bool` · optional
- 🔢 event_active
  > `budget_queues.event_active` · `int4` · required
- ☑️ book_name
  > `budget_queues.book_name` · `bool` · required
- 🕒 fee_kind
  > `budget_queues.fee_kind` · `timestamp` · required
- 🔗 tag_id <!-- orbit link: "#tag_settings_2", status: "foreign key" -->
  > `budget_queues.tag_id` · `int8` · required
  >
  > Points at [`tag_settings_2`](#tag_settings_2).

### audit_links
<!-- orbit
id: "audit_links"
icon: "📗"
collapsed: true
-->
> `audit_links` · table in Members
>
> One row per audit, with 21 columns and about 25 thousand rows.
>
> **Belongs to** [`budget_settings`](#budget_settings) · [`donation_history`](#donation_history) · [`book_items`](#book_items) · [`donation_queues`](#donation_queues)

- 🔑 id <!-- orbit status: "primary key" -->
  > `audit_links.id` · `int8` · required
- 🕒 loan_updated
  > `audit_links.loan_updated` · `timestamp` · optional
- 🧩 notice_updated
  > `audit_links.notice_updated` · `jsonb` · required
- 🕒 device_kind
  > `audit_links.device_kind` · `timestamp` · optional
- 🧩 series_ended
  > `audit_links.series_ended` · `jsonb` · optional
- ☑️ card_position
  > `audit_links.card_position` · `bool` · required
- 🔗 shelf_id <!-- orbit link: "#shelf_queues", status: "foreign key" -->
  > `audit_links.shelf_id` · `int8` · required
  >
  > Points at [`shelf_queues`](#shelf_queues).
- 🔢 payment_amount
  > `audit_links.payment_amount` · `int4` · required
- ☑️ book_status
  > `audit_links.book_status` · `bool` · required
- 🔤 donation_format
  > `audit_links.donation_format` · `varchar` · required
- 🔤 donation_state
  > `audit_links.donation_state` · `varchar` · required
- 🔤 shelf_created
  > `audit_links.shelf_created` · `varchar` · optional
- 🔤 tag_region
  > `audit_links.tag_region` · `varchar` · required
- 🧩 copy_label
  > `audit_links.copy_label` · `jsonb` · optional
- 🔤 order_name
  > `audit_links.order_name` · `varchar` · required
- ☑️ shelf_reviewed
  > `audit_links.shelf_reviewed` · `bool` · required
- 🔢 payment_source
  > `audit_links.payment_source` · `int4` · optional
- 🧩 device_label
  > `audit_links.device_label` · `jsonb` · optional
- 🔤 notice_position
  > `audit_links.notice_position` · `varchar` · required
- 🔢 device_count
  > `audit_links.device_count` · `int4` · optional
- 🔗 review_id <!-- orbit link: "#review_versions", status: "foreign key" -->
  > `audit_links.review_id` · `int8` · required
  >
  > Points at [`review_versions`](#review_versions).

## 🔁 Loans & Returns
<!-- orbit
id: "area-loans-returns"
color: "#2E9E5B"
-->
> 9 tables. Largest: [`series_counts`](#series_counts), [`audit_links_2`](#audit_links_2), [`notice_counts`](#notice_counts).
>
> **Linked to** [`branchs`](#branchs) · [`budget_queues`](#budget_queues) · [`license_settings`](#license_settings)

### series_counts
<!-- orbit
id: "series_counts"
icon: "📇"
collapsed: true
-->
> `series_counts` · table in Loans & Returns
>
> One row per series, with 15 columns and about 468 thousand rows.
>
> **Belongs to** [`holds`](#holds)

- 🔑 id <!-- orbit status: "primary key" -->
  > `series_counts.id` · `int8` · required
- 🕒 loan_format
  > `series_counts.loan_format` · `timestamp` · optional
- 🔗 budget_id <!-- orbit link: "#budget_settings_2", status: "foreign key" -->
  > `series_counts.budget_id` · `int8` · required
  >
  > Points at [`budget_settings_2`](#budget_settings_2).
- 🔗 member_id <!-- orbit link: "#member_counts_2", status: "foreign key" -->
  > `series_counts.member_id` · `int8` · required
  >
  > Points at [`member_counts_2`](#member_counts_2).
- 🔗 payment_id <!-- orbit link: "#payment_versions", status: "foreign key" -->
  > `series_counts.payment_id` · `int8` · required
  >
  > Points at [`payment_versions`](#payment_versions).
- 🔗 license_id <!-- orbit link: "#license_queues", status: "foreign key" -->
  > `series_counts.license_id` · `int8` · required
  >
  > Points at [`license_queues`](#license_queues).
- 🔢 copy_priority
  > `series_counts.copy_priority` · `int4` · required
- 🕒 card_title
  > `series_counts.card_title` · `timestamp` · required
- 🧩 role_title
  > `series_counts.role_title` · `jsonb` · required
- 🕒 book_priority
  > `series_counts.book_priority` · `timestamp` · required
- 🔢 fee_label
  > `series_counts.fee_label` · `int4` · optional
- 🕒 staff_format
  > `series_counts.staff_format` · `timestamp` · required
- 🕒 notice_state
  > `series_counts.notice_state` · `timestamp` · required
- 🔗 donation_id <!-- orbit link: "#donation_links", status: "foreign key" -->
  > `series_counts.donation_id` · `int8` · required
  >
  > Points at [`donation_links`](#donation_links).
- 🔤 copy_created
  > `series_counts.copy_created` · `varchar` · optional

### audit_links_2
<!-- orbit
id: "audit_links_2"
icon: "📗"
collapsed: true
-->
> `audit_links_2` · table in Loans & Returns
>
> One row per audit, with 21 columns and about 2 thousand rows.
>
> **Belongs to** [`report_queues`](#report_queues) · [`license_settings`](#license_settings) · [`tag_counts`](#tag_counts)

- 🔑 id <!-- orbit status: "primary key" -->
  > `audit_links_2.id` · `int8` · required
- 🔤 license_ended
  > `audit_links_2.license_ended` · `varchar` · optional
- 🔤 member_count
  > `audit_links_2.member_count` · `varchar` · required
- ☑️ event_format
  > `audit_links_2.event_format` · `bool` · required
- 🔤 member_created
  > `audit_links_2.member_created` · `varchar` · required
- 🔗 fee_id <!-- orbit link: "#fee_requests", status: "foreign key" -->
  > `audit_links_2.fee_id` · `int8` · required
  >
  > Points at [`fee_requests`](#fee_requests).
- 🕒 fee_ended
  > `audit_links_2.fee_ended` · `timestamp` · required
- 🔗 card_id <!-- orbit link: "#card_logs", status: "foreign key" -->
  > `audit_links_2.card_id` · `int8` · required
  >
  > Points at [`card_logs`](#card_logs).
- 🔢 visit_source
  > `audit_links_2.visit_source` · `int4` · optional
- 🔢 repair_region
  > `audit_links_2.repair_region` · `int4` · optional
- ☑️ card_language
  > `audit_links_2.card_language` · `bool` · required
- 🕒 series_created
  > `audit_links_2.series_created` · `timestamp` · optional
- 🔤 donation_region
  > `audit_links_2.donation_region` · `varchar` · optional
- ☑️ staff_name
  > `audit_links_2.staff_name` · `bool` · optional
- 🧩 report_kind
  > `audit_links_2.report_kind` · `jsonb` · optional
- ☑️ loan_archived
  > `audit_links_2.loan_archived` · `bool` · required
- 🔗 budget_id <!-- orbit link: "#budget_settings", status: "foreign key" -->
  > `audit_links_2.budget_id` · `int8` · required
  >
  > Points at [`budget_settings`](#budget_settings).
- ☑️ branch_region
  > `audit_links_2.branch_region` · `bool` · required
- 🔗 visit_id <!-- orbit link: "#visit_queues", status: "foreign key" -->
  > `audit_links_2.visit_id` · `int8` · required
  >
  > Points at [`visit_queues`](#visit_queues).
- 🕒 card_language_2
  > `audit_links_2.card_language_2` · `timestamp` · optional
- 🔢 author_source
  > `audit_links_2.author_source` · `int4` · required

### notice_counts
<!-- orbit
id: "notice_counts"
icon: "🗂️"
collapsed: true
-->
> `notice_counts` · table in Loans & Returns
>
> One row per notice, with 15 columns and about 426 thousand rows.
>
> **Belongs to** [`ebook_logs`](#ebook_logs) · [`device_items`](#device_items) · [`book_links`](#book_links)

- 🔑 id <!-- orbit status: "primary key" -->
  > `notice_counts.id` · `int8` · required
- 🔢 report_archived
  > `notice_counts.report_archived` · `int4` · required
- 🔤 role_status
  > `notice_counts.role_status` · `varchar` · required
- ☑️ visit_active
  > `notice_counts.visit_active` · `bool` · required
- 🧩 visit_external
  > `notice_counts.visit_external` · `jsonb` · required
- 🧩 shelf_started
  > `notice_counts.shelf_started` · `jsonb` · required
- 🕒 author_amount
  > `notice_counts.author_amount` · `timestamp` · optional
- 🧩 tag_source
  > `notice_counts.tag_source` · `jsonb` · required
- 🕒 review_name
  > `notice_counts.review_name` · `timestamp` · optional
- 🔗 hold_id <!-- orbit link: "#hold_links", status: "foreign key" -->
  > `notice_counts.hold_id` · `int8` · required
  >
  > Points at [`hold_links`](#hold_links).
- ☑️ member_label
  > `notice_counts.member_label` · `bool` · optional
- 🔗 rooms_id <!-- orbit link: "#rooms", status: "foreign key" -->
  > `notice_counts.rooms_id` · `int8` · required
  >
  > Points at [`rooms`](#rooms).
- 🕒 event_state
  > `notice_counts.event_state` · `timestamp` · required
- ☑️ payment_label
  > `notice_counts.payment_label` · `bool` · required
- 🧩 visit_reviewed
  > `notice_counts.visit_reviewed` · `jsonb` · required

### shelfs
<!-- orbit
id: "shelfs"
icon: "📇"
collapsed: true
-->
> `shelfs` · table in Loans & Returns
>
> One row per shelfs, with 4 columns and about 423 thousand rows.
>
> **Belongs to** [`tag_counts`](#tag_counts)

- 🔑 id <!-- orbit status: "primary key" -->
  > `shelfs.id` · `int8` · required
- 🕒 role_started
  > `shelfs.role_started` · `timestamp` · optional
- 🔤 donation_public
  > `shelfs.donation_public` · `varchar` · optional
- 🕒 audit_count
  > `shelfs.audit_count` · `timestamp` · required

### tag_settings
<!-- orbit
id: "tag_settings"
icon: "📇"
collapsed: true
-->
> `tag_settings` · table in Loans & Returns
>
> One row per tag, with 4 columns and about 784 thousand rows.
>
> **Belongs to** [`author_links_2`](#author_links_2) · [`fee_requests`](#fee_requests) · [`loan_logs_2`](#loan_logs_2) · [`card_queues`](#card_queues)

- 🔑 id <!-- orbit status: "primary key" -->
  > `tag_settings.id` · `int8` · required
- 🧩 tag_status
  > `tag_settings.tag_status` · `jsonb` · optional
- 🔢 fee_state
  > `tag_settings.fee_state` · `int4` · required
- 🔢 notice_reviewed
  > `tag_settings.notice_reviewed` · `int4` · required

### staff_counts
<!-- orbit
id: "staff_counts"
icon: "📗"
collapsed: true
-->
> `staff_counts` · table in Loans & Returns
>
> One row per staff, with 24 columns and about 697 thousand rows.
>
> **Belongs to** [`copy_links`](#copy_links) · [`ebook_queues`](#ebook_queues)

- 🔑 id <!-- orbit status: "primary key" -->
  > `staff_counts.id` · `int8` · required
- 🧩 role_reviewed
  > `staff_counts.role_reviewed` · `jsonb` · optional
- 🔤 shelf_updated
  > `staff_counts.shelf_updated` · `varchar` · optional
- 🧩 member_created
  > `staff_counts.member_created` · `jsonb` · optional
- 🕒 role_language
  > `staff_counts.role_language` · `timestamp` · optional
- 🔢 hold_updated
  > `staff_counts.hold_updated` · `int4` · required
- 🔤 report_total
  > `staff_counts.report_total` · `varchar` · required
- 🔢 branch_public
  > `staff_counts.branch_public` · `int4` · required
- ☑️ tag_created
  > `staff_counts.tag_created` · `bool` · required
- 🔗 holds_id <!-- orbit link: "#holds", status: "foreign key" -->
  > `staff_counts.holds_id` · `int8` · required
  >
  > Points at [`holds`](#holds).
- ☑️ loan_created
  > `staff_counts.loan_created` · `bool` · required
- 🔢 review_region
  > `staff_counts.review_region` · `int4` · required
- 🔤 series_language
  > `staff_counts.series_language` · `varchar` · required
- 🔢 donation_state
  > `staff_counts.donation_state` · `int4` · required
- 🔤 series_ended
  > `staff_counts.series_ended` · `varchar` · optional
- 🔗 loan_id <!-- orbit link: "#loan_counts", status: "foreign key" -->
  > `staff_counts.loan_id` · `int8` · required
  >
  > Points at [`loan_counts`](#loan_counts).
- ☑️ audit_started
  > `staff_counts.audit_started` · `bool` · optional
- 🧩 donation_amount
  > `staff_counts.donation_amount` · `jsonb` · required
- ☑️ vendor_active
  > `staff_counts.vendor_active` · `bool` · required
- 🧩 order_reviewed
  > `staff_counts.order_reviewed` · `jsonb` · required
- ☑️ author_position
  > `staff_counts.author_position` · `bool` · optional
- 🕒 review_count
  > `staff_counts.review_count` · `timestamp` · required
- 🔢 room_count
  > `staff_counts.room_count` · `int4` · required
- 🔗 license_id <!-- orbit link: "#license_queues", status: "foreign key" -->
  > `staff_counts.license_id` · `int8` · required
  >
  > Points at [`license_queues`](#license_queues).

### audit_settings
<!-- orbit
id: "audit_settings"
icon: "📗"
collapsed: true
-->
> `audit_settings` · table in Loans & Returns
>
> One row per audit, with 20 columns and about 644 thousand rows.
>
> **Belongs to** [`staff_settings`](#staff_settings)

- 🔑 id <!-- orbit status: "primary key" -->
  > `audit_settings.id` · `int8` · required
- 🕒 device_priority
  > `audit_settings.device_priority` · `timestamp` · optional
- 🔗 tag_id <!-- orbit link: "#tag_settings", status: "foreign key" -->
  > `audit_settings.tag_id` · `int8` · required
  >
  > Points at [`tag_settings`](#tag_settings).
- 🧩 tag_kind
  > `audit_settings.tag_kind` · `jsonb` · optional
- 🔗 member_id <!-- orbit link: "#member_counts", status: "foreign key" -->
  > `audit_settings.member_id` · `int8` · required
  >
  > Points at [`member_counts`](#member_counts).
- 🧩 repair_language
  > `audit_settings.repair_language` · `jsonb` · optional
- 🕒 ebook_priority
  > `audit_settings.ebook_priority` · `timestamp` · required
- 🔤 ebook_updated
  > `audit_settings.ebook_updated` · `varchar` · optional
- 🧩 repair_total
  > `audit_settings.repair_total` · `jsonb` · optional
- ☑️ member_total
  > `audit_settings.member_total` · `bool` · optional
- 🔗 order_id <!-- orbit link: "#order_versions", status: "foreign key" -->
  > `audit_settings.order_id` · `int8` · required
  >
  > Points at [`order_versions`](#order_versions).
- 🧩 audit_format
  > `audit_settings.audit_format` · `jsonb` · optional
- 🕒 review_region
  > `audit_settings.review_region` · `timestamp` · optional
- 🔗 budget_id <!-- orbit link: "#budget_queues", status: "foreign key" -->
  > `audit_settings.budget_id` · `int8` · required
  >
  > Points at [`budget_queues`](#budget_queues).
- 🔢 room_archived
  > `audit_settings.room_archived` · `int4` · required
- ☑️ member_label
  > `audit_settings.member_label` · `bool` · optional
- 🔗 event_id <!-- orbit link: "#event_queues", status: "foreign key" -->
  > `audit_settings.event_id` · `int8` · required
  >
  > Points at [`event_queues`](#event_queues).
- 🔤 notice_region
  > `audit_settings.notice_region` · `varchar` · required
- 🔗 license_id <!-- orbit link: "#license_settings", status: "foreign key" -->
  > `audit_settings.license_id` · `int8` · required
  >
  > Points at [`license_settings`](#license_settings).
- 🔗 card_id <!-- orbit link: "#card_links", status: "foreign key" -->
  > `audit_settings.card_id` · `int8` · required
  >
  > Points at [`card_links`](#card_links).

### review_items
<!-- orbit
id: "review_items"
icon: "🧷"
collapsed: true
-->
> `review_items` · table in Loans & Returns
>
> One row per review, with 24 columns and about 74 thousand rows.
>
> **Belongs to** [`donations`](#donations)

- 🔑 id <!-- orbit status: "primary key" -->
  > `review_items.id` · `int8` · required
- 🔗 licenses_id <!-- orbit link: "#licenses", status: "foreign key" -->
  > `review_items.licenses_id` · `int8` · required
  >
  > Points at [`licenses`](#licenses).
- 🔢 copy_external
  > `review_items.copy_external` · `int4` · optional
- 🧩 event_label
  > `review_items.event_label` · `jsonb` · required
- 🔢 role_language
  > `review_items.role_language` · `int4` · optional
- ☑️ budget_reviewed
  > `review_items.budget_reviewed` · `bool` · required
- 🔗 loan_id <!-- orbit link: "#loan_history", status: "foreign key" -->
  > `review_items.loan_id` · `int8` · required
  >
  > Points at [`loan_history`](#loan_history).
- 🔤 ebook_name
  > `review_items.ebook_name` · `varchar` · required
- 🔗 visit_id <!-- orbit link: "#visit_links", status: "foreign key" -->
  > `review_items.visit_id` · `int8` · required
  >
  > Points at [`visit_links`](#visit_links).
- 🔢 payment_active
  > `review_items.payment_active` · `int4` · optional
- ☑️ shelf_source
  > `review_items.shelf_source` · `bool` · required
- 🧩 shelf_reviewed
  > `review_items.shelf_reviewed` · `jsonb` · optional
- 🔢 series_name
  > `review_items.series_name` · `int4` · optional
- ☑️ donation_source
  > `review_items.donation_source` · `bool` · required
- 🔤 copy_reviewed
  > `review_items.copy_reviewed` · `varchar` · required
- 🔗 audit_id <!-- orbit link: "#audit_links", status: "foreign key" -->
  > `review_items.audit_id` · `int8` · required
  >
  > Points at [`audit_links`](#audit_links).
- 🕒 copy_state
  > `review_items.copy_state` · `timestamp` · optional
- 🔗 member_id <!-- orbit link: "#member_history", status: "foreign key" -->
  > `review_items.member_id` · `int8` · required
  >
  > Points at [`member_history`](#member_history).
- ☑️ license_total
  > `review_items.license_total` · `bool` · required
- 🔗 role_id <!-- orbit link: "#role_counts", status: "foreign key" -->
  > `review_items.role_id` · `int8` · required
  >
  > Points at [`role_counts`](#role_counts).
- 🔢 branch_created
  > `review_items.branch_created` · `int4` · optional
- ☑️ card_created
  > `review_items.card_created` · `bool` · optional
- ☑️ room_total
  > `review_items.room_total` · `bool` · optional
- 🔤 report_started
  > `review_items.report_started` · `varchar` · required

### staff_items
<!-- orbit
id: "staff_items"
icon: "📗"
collapsed: true
-->
> `staff_items` · table in Loans & Returns
>
> One row per staff, with 12 columns and about 118 thousand rows.
>
> **Belongs to** [`audit_links_2`](#audit_links_2) · [`copy_versions`](#copy_versions) · [`budgets`](#budgets)

- 🔑 id <!-- orbit status: "primary key" -->
  > `staff_items.id` · `int8` · required
- 🔢 device_source
  > `staff_items.device_source` · `int4` · required
- 🔢 staff_state
  > `staff_items.staff_state` · `int4` · required
- 🧩 review_notes
  > `staff_items.review_notes` · `jsonb` · optional
- 🧩 vendor_total
  > `staff_items.vendor_total` · `jsonb` · required
- 🔢 tag_region
  > `staff_items.tag_region` · `int4` · required
- 🔢 branch_total
  > `staff_items.branch_total` · `int4` · optional
- 🔤 ebook_amount
  > `staff_items.ebook_amount` · `varchar` · required
- 🕒 payment_position
  > `staff_items.payment_position` · `timestamp` · optional
- 🔢 role_position
  > `staff_items.role_position` · `int4` · optional
- 🔤 copy_state
  > `staff_items.copy_state` · `varchar` · optional
- ☑️ ebook_code
  > `staff_items.ebook_code` · `bool` · optional

## 🏛️ Branches
<!-- orbit
id: "area-branches"
color: "#8E4EC6"
-->
> 10 tables. Largest: [`tag_counts`](#tag_counts), [`role_counts`](#role_counts), [`branch_links`](#branch_links).
>
> **Linked to** [`loan_logs`](#loan_logs) · [`copy_versions`](#copy_versions) · [`vendor_items`](#vendor_items)

### tag_counts
<!-- orbit
id: "tag_counts"
icon: "🧷"
collapsed: true
-->
> `tag_counts` · table in Branches
>
> One row per tag, with 17 columns and about 389 thousand rows.
>
> **Belongs to** [`review_versions`](#review_versions) · [`member_counts_2`](#member_counts_2) · [`hold_history`](#hold_history)

- 🔑 id <!-- orbit status: "primary key" -->
  > `tag_counts.id` · `int8` · required
- 🧩 member_updated
  > `tag_counts.member_updated` · `jsonb` · optional
- 🔗 review_id <!-- orbit link: "#review_items", status: "foreign key" -->
  > `tag_counts.review_id` · `int8` · required
  >
  > Points at [`review_items`](#review_items).
- 🔗 licenses_id <!-- orbit link: "#licenses", status: "foreign key" -->
  > `tag_counts.licenses_id` · `int8` · required
  >
  > Points at [`licenses`](#licenses).
- 🕒 room_code
  > `tag_counts.room_code` · `timestamp` · optional
- 🔢 shelf_status
  > `tag_counts.shelf_status` · `int4` · optional
- 🔤 payment_count
  > `tag_counts.payment_count` · `varchar` · optional
- 🔢 book_reviewed
  > `tag_counts.book_reviewed` · `int4` · optional
- 🧩 room_public
  > `tag_counts.room_public` · `jsonb` · required
- 🔤 budget_archived
  > `tag_counts.budget_archived` · `varchar` · optional
- 🔗 order_id <!-- orbit link: "#order_logs", status: "foreign key" -->
  > `tag_counts.order_id` · `int8` · required
  >
  > Points at [`order_logs`](#order_logs).
- 🔗 ebook_id <!-- orbit link: "#ebook_queues", status: "foreign key" -->
  > `tag_counts.ebook_id` · `int8` · required
  >
  > Points at [`ebook_queues`](#ebook_queues).
- 🔗 report_id <!-- orbit link: "#report_queues_2", status: "foreign key" -->
  > `tag_counts.report_id` · `int8` · required
  >
  > Points at [`report_queues_2`](#report_queues_2).
- 🕒 shelf_name
  > `tag_counts.shelf_name` · `timestamp` · optional
- 🔢 repair_public
  > `tag_counts.repair_public` · `int4` · optional
- 🧩 license_reviewed
  > `tag_counts.license_reviewed` · `jsonb` · optional
- 🔤 book_public
  > `tag_counts.book_public` · `varchar` · optional

### role_counts
<!-- orbit
id: "role_counts"
icon: "📗"
collapsed: true
-->
> `role_counts` · table in Branches
>
> One row per role, with 24 columns and about 767 thousand rows.
>
> **Belongs to** [`shelfs`](#shelfs) · [`visit_items`](#visit_items)

- 🔑 id <!-- orbit status: "primary key" -->
  > `role_counts.id` · `int8` · required
- ☑️ review_language
  > `role_counts.review_language` · `bool` · optional
- ☑️ copy_name
  > `role_counts.copy_name` · `bool` · optional
- 🔗 loan_id <!-- orbit link: "#loan_logs", status: "foreign key" -->
  > `role_counts.loan_id` · `int8` · required
  >
  > Points at [`loan_logs`](#loan_logs).
- ☑️ member_name
  > `role_counts.member_name` · `bool` · required
- 🧩 order_reviewed
  > `role_counts.order_reviewed` · `jsonb` · optional
- 🔤 shelf_active
  > `role_counts.shelf_active` · `varchar` · optional
- 🔢 donation_position
  > `role_counts.donation_position` · `int4` · required
- 🔢 loan_priority
  > `role_counts.loan_priority` · `int4` · optional
- 🔗 notice_id <!-- orbit link: "#notice_counts", status: "foreign key" -->
  > `role_counts.notice_id` · `int8` · required
  >
  > Points at [`notice_counts`](#notice_counts).
- ☑️ notice_ended
  > `role_counts.notice_ended` · `bool` · required
- 🔢 tag_total
  > `role_counts.tag_total` · `int4` · optional
- ☑️ vendor_priority
  > `role_counts.vendor_priority` · `bool` · required
- 🔗 hold_id <!-- orbit link: "#hold_logs", status: "foreign key" -->
  > `role_counts.hold_id` · `int8` · required
  >
  > Points at [`hold_logs`](#hold_logs).
- 🔢 vendor_reviewed
  > `role_counts.vendor_reviewed` · `int4` · required
- 🔢 order_updated
  > `role_counts.order_updated` · `int4` · optional
- 🔤 room_started
  > `role_counts.room_started` · `varchar` · required
- 🕒 license_source
  > `role_counts.license_source` · `timestamp` · optional
- 🕒 loan_format
  > `role_counts.loan_format` · `timestamp` · optional
- 🔢 series_notes
  > `role_counts.series_notes` · `int4` · optional
- 🕒 report_public
  > `role_counts.report_public` · `timestamp` · required
- 🔗 fee_id <!-- orbit link: "#fee_requests", status: "foreign key" -->
  > `role_counts.fee_id` · `int8` · required
  >
  > Points at [`fee_requests`](#fee_requests).
- ☑️ notice_created
  > `role_counts.notice_created` · `bool` · required
- 🕒 order_status
  > `role_counts.order_status` · `timestamp` · optional

### branch_links
<!-- orbit
id: "branch_links"
icon: "📗"
collapsed: true
-->
> `branch_links` · table in Branches
>
> One row per branch, with 20 columns and about 423 thousand rows.
>
> **Belongs to** [`room_counts`](#room_counts)

- 🔑 id <!-- orbit status: "primary key" -->
  > `branch_links.id` · `int8` · required
- 🔗 author_id <!-- orbit link: "#author_history", status: "foreign key" -->
  > `branch_links.author_id` · `int8` · required
  >
  > Points at [`author_history`](#author_history).
- 🔗 report_id <!-- orbit link: "#report_items", status: "foreign key" -->
  > `branch_links.report_id` · `int8` · required
  >
  > Points at [`report_items`](#report_items).
- ☑️ staff_kind
  > `branch_links.staff_kind` · `bool` · optional
- 🕒 hold_language
  > `branch_links.hold_language` · `timestamp` · optional
- 🔢 loan_archived
  > `branch_links.loan_archived` · `int4` · required
- ☑️ event_started
  > `branch_links.event_started` · `bool` · optional
- 🔢 author_updated
  > `branch_links.author_updated` · `int4` · optional
- 🔤 staff_reviewed
  > `branch_links.staff_reviewed` · `varchar` · optional
- 🔗 repair_id <!-- orbit link: "#repair_requests", status: "foreign key" -->
  > `branch_links.repair_id` · `int8` · required
  >
  > Points at [`repair_requests`](#repair_requests).
- ☑️ notice_code
  > `branch_links.notice_code` · `bool` · required
- 🧩 repair_total
  > `branch_links.repair_total` · `jsonb` · optional
- 🧩 hold_created
  > `branch_links.hold_created` · `jsonb` · optional
- 🔢 review_updated
  > `branch_links.review_updated` · `int4` · required
- ☑️ series_amount
  > `branch_links.series_amount` · `bool` · optional
- 🔗 tags_id <!-- orbit link: "#tags", status: "foreign key" -->
  > `branch_links.tags_id` · `int8` · required
  >
  > Points at [`tags`](#tags).
- 🔗 author_id_2 <!-- orbit link: "#author_links_2", status: "foreign key" -->
  > `branch_links.author_id_2` · `int8` · required
  >
  > Points at [`author_links_2`](#author_links_2).
- 🔢 branch_active
  > `branch_links.branch_active` · `int4` · required
- 🔗 review_id <!-- orbit link: "#review_links", status: "foreign key" -->
  > `branch_links.review_id` · `int8` · required
  >
  > Points at [`review_links`](#review_links).
- 🔢 shelf_status
  > `branch_links.shelf_status` · `int4` · required

### hold_links
<!-- orbit
id: "hold_links"
icon: "📇"
collapsed: true
-->
> `hold_links` · table in Branches
>
> One row per hold, with 14 columns and about 194 thousand rows.
>
> **Belongs to** [`review_queues`](#review_queues) · [`order_items`](#order_items)

- 🔑 id <!-- orbit status: "primary key" -->
  > `hold_links.id` · `int8` · required
- 🔤 audit_external
  > `hold_links.audit_external` · `varchar` · required
- 🔢 review_ended
  > `hold_links.review_ended` · `int4` · optional
- 🔢 copy_format
  > `hold_links.copy_format` · `int4` · optional
- 🧩 notice_amount
  > `hold_links.notice_amount` · `jsonb` · required
- 🔤 vendor_state
  > `hold_links.vendor_state` · `varchar` · optional
- 🧩 budget_amount
  > `hold_links.budget_amount` · `jsonb` · required
- 🕒 room_status
  > `hold_links.room_status` · `timestamp` · optional
- 🔢 license_format
  > `hold_links.license_format` · `int4` · required
- 🔗 visit_id <!-- orbit link: "#visit_queues", status: "foreign key" -->
  > `hold_links.visit_id` · `int8` · required
  >
  > Points at [`visit_queues`](#visit_queues).
- 🔤 repair_format
  > `hold_links.repair_format` · `varchar` · optional
- 🔤 repair_public
  > `hold_links.repair_public` · `varchar` · optional
- 🔢 copy_active
  > `hold_links.copy_active` · `int4` · required
- 🔤 branch_position
  > `hold_links.branch_position` · `varchar` · required

### hold_logs
<!-- orbit
id: "hold_logs"
icon: "📇"
collapsed: true
-->
> `hold_logs` · table in Branches
>
> One row per hold, with 13 columns and about 798 thousand rows.
>
> **Belongs to** [`report_queues`](#report_queues) · [`tag_counts`](#tag_counts)

- 🔑 id <!-- orbit status: "primary key" -->
  > `hold_logs.id` · `int8` · required
- 🔗 copy_id <!-- orbit link: "#copy_history", status: "foreign key" -->
  > `hold_logs.copy_id` · `int8` · required
  >
  > Points at [`copy_history`](#copy_history).
- 🔢 loan_total
  > `hold_logs.loan_total` · `int4` · optional
- 🔤 order_reviewed
  > `hold_logs.order_reviewed` · `varchar` · optional
- 🔤 hold_state
  > `hold_logs.hold_state` · `varchar` · required
- 🕒 report_format
  > `hold_logs.report_format` · `timestamp` · optional
- 🔤 loan_title
  > `hold_logs.loan_title` · `varchar` · optional
- 🕒 tag_source
  > `hold_logs.tag_source` · `timestamp` · required
- 🧩 card_updated
  > `hold_logs.card_updated` · `jsonb` · optional
- 🕒 branch_archived
  > `hold_logs.branch_archived` · `timestamp` · required
- 🔢 license_external
  > `hold_logs.license_external` · `int4` · optional
- 🔤 staff_public
  > `hold_logs.staff_public` · `varchar` · optional
- 🔤 device_format
  > `hold_logs.device_format` · `varchar` · required

### holds
<!-- orbit
id: "holds"
icon: "🗃️"
collapsed: true
-->
> `holds` · table in Branches
>
> One row per holds, with 7 columns and about 261 thousand rows.
>
> **Belongs to** [`fee_history`](#fee_history) · [`vendor_items`](#vendor_items) · [`notice_counts`](#notice_counts)

- 🔑 id <!-- orbit status: "primary key" -->
  > `holds.id` · `int8` · required
- 🕒 role_region
  > `holds.role_region` · `timestamp` · required
- ☑️ audit_kind
  > `holds.audit_kind` · `bool` · required
- 🔤 budget_total
  > `holds.budget_total` · `varchar` · optional
- 🔢 visit_public
  > `holds.visit_public` · `int4` · optional
- 🔤 shelf_ended
  > `holds.shelf_ended` · `varchar` · optional
- 🔢 role_public
  > `holds.role_public` · `int4` · optional

### card_queues
<!-- orbit
id: "card_queues"
icon: "🧷"
collapsed: true
-->
> `card_queues` · table in Branches
>
> One row per card, with 24 columns and about 413 thousand rows.
>
> **Belongs to** [`series_counts`](#series_counts) · [`report_logs_2`](#report_logs_2) · [`audit_queues`](#audit_queues)

- 🔑 id <!-- orbit status: "primary key" -->
  > `card_queues.id` · `int8` · required
- 🧩 fee_title
  > `card_queues.fee_title` · `jsonb` · required
- 🧩 visit_ended
  > `card_queues.visit_ended` · `jsonb` · optional
- 🔗 book_id <!-- orbit link: "#book_settings", status: "foreign key" -->
  > `card_queues.book_id` · `int8` · required
  >
  > Points at [`book_settings`](#book_settings).
- 🔢 payment_ended
  > `card_queues.payment_ended` · `int4` · optional
- 🕒 room_active
  > `card_queues.room_active` · `timestamp` · required
- 🕒 staff_updated
  > `card_queues.staff_updated` · `timestamp` · optional
- 🔗 budgets_id <!-- orbit link: "#budgets", status: "foreign key" -->
  > `card_queues.budgets_id` · `int8` · required
  >
  > Points at [`budgets`](#budgets).
- 🧩 copy_state
  > `card_queues.copy_state` · `jsonb` · required
- 🕒 audit_total
  > `card_queues.audit_total` · `timestamp` · optional
- 🕒 shelf_external
  > `card_queues.shelf_external` · `timestamp` · optional
- ☑️ fee_code
  > `card_queues.fee_code` · `bool` · required
- 🔗 copy_id <!-- orbit link: "#copy_versions", status: "foreign key" -->
  > `card_queues.copy_id` · `int8` · required
  >
  > Points at [`copy_versions`](#copy_versions).
- 🔢 staff_reviewed
  > `card_queues.staff_reviewed` · `int4` · optional
- 🔢 visit_status
  > `card_queues.visit_status` · `int4` · required
- 🔢 vendor_format
  > `card_queues.vendor_format` · `int4` · optional
- 🔤 member_active
  > `card_queues.member_active` · `varchar` · optional
- ☑️ event_label
  > `card_queues.event_label` · `bool` · required
- 🔢 room_position
  > `card_queues.room_position` · `int4` · required
- 🔗 vendor_id <!-- orbit link: "#vendor_history", status: "foreign key" -->
  > `card_queues.vendor_id` · `int8` · required
  >
  > Points at [`vendor_history`](#vendor_history).
- 🕒 order_external
  > `card_queues.order_external` · `timestamp` · optional
- ☑️ fee_total
  > `card_queues.fee_total` · `bool` · optional
- 🔤 repair_active
  > `card_queues.repair_active` · `varchar` · optional
- 🕒 license_external
  > `card_queues.license_external` · `timestamp` · required

### hold_history
<!-- orbit
id: "hold_history"
icon: "🗃️"
collapsed: true
-->
> `hold_history` · table in Branches
>
> One row per hold, with 14 columns and about 438 thousand rows.
>
> **Belongs to** [`review_items`](#review_items) · [`event_history`](#event_history) · [`repair_requests`](#repair_requests)

- 🔑 id <!-- orbit status: "primary key" -->
  > `hold_history.id` · `int8` · required
- ☑️ repair_active
  > `hold_history.repair_active` · `bool` · required
- 🔢 room_started
  > `hold_history.room_started` · `int4` · required
- 🔤 loan_amount
  > `hold_history.loan_amount` · `varchar` · optional
- 🧩 branch_kind
  > `hold_history.branch_kind` · `jsonb` · required
- 🧩 visit_external
  > `hold_history.visit_external` · `jsonb` · required
- 🔢 member_kind
  > `hold_history.member_kind` · `int4` · required
- 🔢 series_label
  > `hold_history.series_label` · `int4` · required
- 🧩 donation_name
  > `hold_history.donation_name` · `jsonb` · required
- 🔢 visit_region
  > `hold_history.visit_region` · `int4` · required
- 🔢 shelf_status
  > `hold_history.shelf_status` · `int4` · optional
- 🕒 series_count
  > `hold_history.series_count` · `timestamp` · optional
- 🧩 notice_count
  > `hold_history.notice_count` · `jsonb` · optional
- 🧩 review_amount
  > `hold_history.review_amount` · `jsonb` · optional

### room_requests
<!-- orbit
id: "room_requests"
icon: "🗂️"
collapsed: true
-->
> `room_requests` · table in Branches
>
> One row per room, with 20 columns and about 874 thousand rows.
>
> **Belongs to** [`tag_settings_2`](#tag_settings_2)

- 🔑 id <!-- orbit status: "primary key" -->
  > `room_requests.id` · `int8` · required
- 🧩 branch_code
  > `room_requests.branch_code` · `jsonb` · optional
- ☑️ ebook_language
  > `room_requests.ebook_language` · `bool` · required
- 🔗 card_id <!-- orbit link: "#card_links", status: "foreign key" -->
  > `room_requests.card_id` · `int8` · required
  >
  > Points at [`card_links`](#card_links).
- 🔤 staff_name
  > `room_requests.staff_name` · `varchar` · required
- ☑️ event_state
  > `room_requests.event_state` · `bool` · optional
- 🕒 event_amount
  > `room_requests.event_amount` · `timestamp` · optional
- 🧩 staff_status
  > `room_requests.staff_status` · `jsonb` · required
- ☑️ report_external
  > `room_requests.report_external` · `bool` · optional
- 🔤 book_position
  > `room_requests.book_position` · `varchar` · optional
- 🕒 report_ended
  > `room_requests.report_ended` · `timestamp` · optional
- 🔗 license_id <!-- orbit link: "#license_versions", status: "foreign key" -->
  > `room_requests.license_id` · `int8` · required
  >
  > Points at [`license_versions`](#license_versions).
- 🕒 fee_total
  > `room_requests.fee_total` · `timestamp` · optional
- 🔤 event_total
  > `room_requests.event_total` · `varchar` · required
- 🔗 device_id <!-- orbit link: "#device_items", status: "foreign key" -->
  > `room_requests.device_id` · `int8` · required
  >
  > Points at [`device_items`](#device_items).
- 🧩 room_state
  > `room_requests.room_state` · `jsonb` · required
- 🔢 repair_name
  > `room_requests.repair_name` · `int4` · required
- 🔗 device_id_2 <!-- orbit link: "#device_items", status: "foreign key" -->
  > `room_requests.device_id_2` · `int8` · required
  >
  > Points at [`device_items`](#device_items).
- 🔢 tag_ended
  > `room_requests.tag_ended` · `int4` · optional
- ☑️ notice_region
  > `room_requests.notice_region` · `bool` · required

### review_links
<!-- orbit
id: "review_links"
icon: "🧮"
collapsed: true
-->
> `review_links` · table in Branches
>
> One row per review, with 20 columns and about 531 thousand rows.
>
> **Belongs to** [`report_items`](#report_items) · [`copy_items`](#copy_items) · [`order_items`](#order_items)

- 🔑 id <!-- orbit status: "primary key" -->
  > `review_links.id` · `int8` · required
- 🔗 member_id <!-- orbit link: "#member_versions", status: "foreign key" -->
  > `review_links.member_id` · `int8` · required
  >
  > Points at [`member_versions`](#member_versions).
- 🔗 visits_id <!-- orbit link: "#visits", status: "foreign key" -->
  > `review_links.visits_id` · `int8` · required
  >
  > Points at [`visits`](#visits).
- 🔗 series_id <!-- orbit link: "#series_links", status: "foreign key" -->
  > `review_links.series_id` · `int8` · required
  >
  > Points at [`series_links`](#series_links).
- 🔗 license_id <!-- orbit link: "#license_queues", status: "foreign key" -->
  > `review_links.license_id` · `int8` · required
  >
  > Points at [`license_queues`](#license_queues).
- 🔢 hold_notes
  > `review_links.hold_notes` · `int4` · required
- 🔗 copy_id <!-- orbit link: "#copy_versions", status: "foreign key" -->
  > `review_links.copy_id` · `int8` · required
  >
  > Points at [`copy_versions`](#copy_versions).
- 🔗 visits_id_2 <!-- orbit link: "#visits", status: "foreign key" -->
  > `review_links.visits_id_2` · `int8` · required
  >
  > Points at [`visits`](#visits).
- 🕒 card_position
  > `review_links.card_position` · `timestamp` · optional
- 🕒 shelf_source
  > `review_links.shelf_source` · `timestamp` · optional
- ☑️ room_active
  > `review_links.room_active` · `bool` · optional
- 🕒 audit_started
  > `review_links.audit_started` · `timestamp` · required
- 🧩 ebook_title
  > `review_links.ebook_title` · `jsonb` · required
- 🕒 card_language
  > `review_links.card_language` · `timestamp` · required
- 🕒 notice_reviewed
  > `review_links.notice_reviewed` · `timestamp` · required
- ☑️ author_active
  > `review_links.author_active` · `bool` · optional
- 🔗 loan_id <!-- orbit link: "#loan_logs_2", status: "foreign key" -->
  > `review_links.loan_id` · `int8` · required
  >
  > Points at [`loan_logs_2`](#loan_logs_2).
- ☑️ notice_count
  > `review_links.notice_count` · `bool` · optional
- 🔤 author_region
  > `review_links.author_region` · `varchar` · required
- 🔗 report_id <!-- orbit link: "#report_links", status: "foreign key" -->
  > `review_links.report_id` · `int8` · required
  >
  > Points at [`report_links`](#report_links).

## 🗓️ Events
<!-- orbit
id: "area-events"
color: "#E5484D"
-->
> 10 tables. Largest: [`author_links_2`](#author_links_2), [`shelf_queues`](#shelf_queues), [`fee_requests`](#fee_requests).
>
> **Linked to** [`order_logs`](#order_logs) · [`report_logs`](#report_logs) · [`ebook_requests`](#ebook_requests)

### author_links_2
<!-- orbit
id: "author_links_2"
icon: "🧷"
collapsed: true
-->
> `author_links_2` · table in Events
>
> One row per author, with 260 columns and about 52 thousand rows.
>
> **Belongs to** [`device_settings`](#device_settings)

- 🔑 id <!-- orbit status: "primary key" -->
  > `author_links_2.id` · `int8` · required
- 🔗 order_id <!-- orbit link: "#order_links", status: "foreign key" -->
  > `author_links_2.order_id` · `int8` · required
  >
  > Points at [`order_links`](#order_links).
- 🔗 hold_id <!-- orbit link: "#hold_history", status: "foreign key" -->
  > `author_links_2.hold_id` · `int8` · required
  >
  > Points at [`hold_history`](#hold_history).
- ☑️ repair_format
  > `author_links_2.repair_format` · `bool` · required
- 🕒 ebook_count
  > `author_links_2.ebook_count` · `timestamp` · optional
- 🔢 copy_priority
  > `author_links_2.copy_priority` · `int4` · optional
- ☑️ book_notes
  > `author_links_2.book_notes` · `bool` · optional
- 🔗 review_id <!-- orbit link: "#review_counts", status: "foreign key" -->
  > `author_links_2.review_id` · `int8` · required
  >
  > Points at [`review_counts`](#review_counts).
- 🔤 role_active
  > `author_links_2.role_active` · `varchar` · required
- 🕒 license_region
  > `author_links_2.license_region` · `timestamp` · optional
- 🕒 vendor_region
  > `author_links_2.vendor_region` · `timestamp` · optional
- 🧩 notice_kind
  > `author_links_2.notice_kind` · `jsonb` · optional
- 🕒 payment_kind
  > `author_links_2.payment_kind` · `timestamp` · required
- 🧩 visit_public
  > `author_links_2.visit_public` · `jsonb` · optional
- 🕒 donation_status
  > `author_links_2.donation_status` · `timestamp` · required
- 🔗 ebook_id <!-- orbit link: "#ebook_requests", status: "foreign key" -->
  > `author_links_2.ebook_id` · `int8` · required
  >
  > Points at [`ebook_requests`](#ebook_requests).
- 🕒 event_active
  > `author_links_2.event_active` · `timestamp` · required
- 🔗 visits_id <!-- orbit link: "#visits", status: "foreign key" -->
  > `author_links_2.visits_id` · `int8` · required
  >
  > Points at [`visits`](#visits).
- 🔗 series_id <!-- orbit link: "#series_requests", status: "foreign key" -->
  > `author_links_2.series_id` · `int8` · required
  >
  > Points at [`series_requests`](#series_requests).
- 🧩 notice_total
  > `author_links_2.notice_total` · `jsonb` · optional
- 🧩 payment_ended
  > `author_links_2.payment_ended` · `jsonb` · optional
- 🔢 visit_count
  > `author_links_2.visit_count` · `int4` · required
- 🕒 donation_position
  > `author_links_2.donation_position` · `timestamp` · required
- 🔗 copy_id <!-- orbit link: "#copy_versions", status: "foreign key" -->
  > `author_links_2.copy_id` · `int8` · required
  >
  > Points at [`copy_versions`](#copy_versions).
- ☑️ book_label
  > `author_links_2.book_label` · `bool` · required
- 🕒 fee_label
  > `author_links_2.fee_label` · `timestamp` · optional
- 🔢 budget_status
  > `author_links_2.budget_status` · `int4` · required
- 🔢 copy_source
  > `author_links_2.copy_source` · `int4` · required
- 🔤 audit_notes
  > `author_links_2.audit_notes` · `varchar` · required
- ☑️ review_title
  > `author_links_2.review_title` · `bool` · optional
- 🕒 author_title
  > `author_links_2.author_title` · `timestamp` · optional
- ☑️ notice_started
  > `author_links_2.notice_started` · `bool` · required
- 🔢 event_state
  > `author_links_2.event_state` · `int4` · optional
- 🧩 visit_total
  > `author_links_2.visit_total` · `jsonb` · required
- 🔗 license_id <!-- orbit link: "#license_queues_2", status: "foreign key" -->
  > `author_links_2.license_id` · `int8` · required
  >
  > Points at [`license_queues_2`](#license_queues_2).
- 🔗 report_id <!-- orbit link: "#report_queues", status: "foreign key" -->
  > `author_links_2.report_id` · `int8` · required
  >
  > Points at [`report_queues`](#report_queues).
- 🔗 room_id <!-- orbit link: "#room_settings", status: "foreign key" -->
  > `author_links_2.room_id` · `int8` · required
  >
  > Points at [`room_settings`](#room_settings).
- ☑️ series_status
  > `author_links_2.series_status` · `bool` · optional
- ☑️ loan_notes
  > `author_links_2.loan_notes` · `bool` · required
- 🔢 branch_amount
  > `author_links_2.branch_amount` · `int4` · required
- ☑️ branch_priority
  > `author_links_2.branch_priority` · `bool` · optional
- 🔤 tag_title
  > `author_links_2.tag_title` · `varchar` · optional
- 🔗 seriess_id <!-- orbit link: "#seriess", status: "foreign key" -->
  > `author_links_2.seriess_id` · `int8` · required
  >
  > Points at [`seriess`](#seriess).
- 🕒 device_ended
  > `author_links_2.device_ended` · `timestamp` · required
- 🧩 event_format
  > `author_links_2.event_format` · `jsonb` · required
- 🔗 review_id_2 <!-- orbit link: "#review_links", status: "foreign key" -->
  > `author_links_2.review_id_2` · `int8` · required
  >
  > Points at [`review_links`](#review_links).
- 🧩 license_status
  > `author_links_2.license_status` · `jsonb` · optional
- 🧩 card_notes
  > `author_links_2.card_notes` · `jsonb` · required
- ☑️ hold_notes
  > `author_links_2.hold_notes` · `bool` · optional
- 🔢 role_reviewed
  > `author_links_2.role_reviewed` · `int4` · optional
- ☑️ budget_position
  > `author_links_2.budget_position` · `bool` · optional
- 🕒 role_language
  > `author_links_2.role_language` · `timestamp` · required
- 🧩 review_title_2
  > `author_links_2.review_title_2` · `jsonb` · required
- 🔗 license_id_2 <!-- orbit link: "#license_logs_2", status: "foreign key" -->
  > `author_links_2.license_id_2` · `int8` · required
  >
  > Points at [`license_logs_2`](#license_logs_2).
- ☑️ order_total
  > `author_links_2.order_total` · `bool` · optional
- 🔗 tag_id <!-- orbit link: "#tag_requests", status: "foreign key" -->
  > `author_links_2.tag_id` · `int8` · required
  >
  > Points at [`tag_requests`](#tag_requests).
- 🕒 loan_code
  > `author_links_2.loan_code` · `timestamp` · optional
- 🔗 loan_id <!-- orbit link: "#loan_history", status: "foreign key" -->
  > `author_links_2.loan_id` · `int8` · required
  >
  > Points at [`loan_history`](#loan_history).
- 🧩 notice_updated
  > `author_links_2.notice_updated` · `jsonb` · required
- 🕒 hold_reviewed
  > `author_links_2.hold_reviewed` · `timestamp` · optional
- 🧩 staff_position
  > `author_links_2.staff_position` · `jsonb` · optional
- 🕒 hold_notes_2
  > `author_links_2.hold_notes_2` · `timestamp` · required
- 🔗 holds_id <!-- orbit link: "#holds", status: "foreign key" -->
  > `author_links_2.holds_id` · `int8` · required
  >
  > Points at [`holds`](#holds).
- 🔗 event_id <!-- orbit link: "#event_history", status: "foreign key" -->
  > `author_links_2.event_id` · `int8` · required
  >
  > Points at [`event_history`](#event_history).
- ☑️ event_ended
  > `author_links_2.event_ended` · `bool` · optional
- 🔗 ebook_id_2 <!-- orbit link: "#ebook_requests", status: "foreign key" -->
  > `author_links_2.ebook_id_2` · `int8` · required
  >
  > Points at [`ebook_requests`](#ebook_requests).
- 🔤 tag_total
  > `author_links_2.tag_total` · `varchar` · optional
- 🕒 member_status
  > `author_links_2.member_status` · `timestamp` · optional
- ☑️ device_region
  > `author_links_2.device_region` · `bool` · optional
- 🔗 notice_id <!-- orbit link: "#notice_counts", status: "foreign key" -->
  > `author_links_2.notice_id` · `int8` · required
  >
  > Points at [`notice_counts`](#notice_counts).
- 🕒 device_source
  > `author_links_2.device_source` · `timestamp` · optional
- 🔢 event_label
  > `author_links_2.event_label` · `int4` · optional
- ☑️ budget_count
  > `author_links_2.budget_count` · `bool` · required
- 🔗 review_id_3 <!-- orbit link: "#review_counts", status: "foreign key" -->
  > `author_links_2.review_id_3` · `int8` · required
  >
  > Points at [`review_counts`](#review_counts).
- 🔢 role_source
  > `author_links_2.role_source` · `int4` · required
- 🕒 series_status_2
  > `author_links_2.series_status_2` · `timestamp` · required
- 🔗 review_id_4 <!-- orbit link: "#review_counts", status: "foreign key" -->
  > `author_links_2.review_id_4` · `int8` · required
  >
  > Points at [`review_counts`](#review_counts).
- 🔗 report_id_2 <!-- orbit link: "#report_queues_2", status: "foreign key" -->
  > `author_links_2.report_id_2` · `int8` · required
  >
  > Points at [`report_queues_2`](#report_queues_2).
- 🧩 author_updated
  > `author_links_2.author_updated` · `jsonb` · required
- ☑️ repair_format_2
  > `author_links_2.repair_format_2` · `bool` · optional
- 🕒 tag_status
  > `author_links_2.tag_status` · `timestamp` · required
- 🔢 budget_kind
  > `author_links_2.budget_kind` · `int4` · required
- 🕒 shelf_updated
  > `author_links_2.shelf_updated` · `timestamp` · required
- 🕒 shelf_language
  > `author_links_2.shelf_language` · `timestamp` · required
- 🕒 visit_created
  > `author_links_2.visit_created` · `timestamp` · optional
- 🔤 review_count
  > `author_links_2.review_count` · `varchar` · required
- 🔗 ebook_id_3 <!-- orbit link: "#ebook_logs", status: "foreign key" -->
  > `author_links_2.ebook_id_3` · `int8` · required
  >
  > Points at [`ebook_logs`](#ebook_logs).
- 🔗 branch_id <!-- orbit link: "#branch_links", status: "foreign key" -->
  > `author_links_2.branch_id` · `int8` · required
  >
  > Points at [`branch_links`](#branch_links).
- 🔗 order_id_2 <!-- orbit link: "#order_items", status: "foreign key" -->
  > `author_links_2.order_id_2` · `int8` · required
  >
  > Points at [`order_items`](#order_items).
- 🔤 member_priority
  > `author_links_2.member_priority` · `varchar` · required
- 🔢 repair_reviewed
  > `author_links_2.repair_reviewed` · `int4` · required
- ☑️ event_position
  > `author_links_2.event_position` · `bool` · optional
- 🕒 license_state
  > `author_links_2.license_state` · `timestamp` · optional
- 🔤 series_source
  > `author_links_2.series_source` · `varchar` · optional
- ☑️ fee_started
  > `author_links_2.fee_started` · `bool` · required
- 🔢 copy_updated
  > `author_links_2.copy_updated` · `int4` · required
- 🔤 order_priority
  > `author_links_2.order_priority` · `varchar` · optional
- 🔗 room_id_2 <!-- orbit link: "#room_logs", status: "foreign key" -->
  > `author_links_2.room_id_2` · `int8` · required
  >
  > Points at [`room_logs`](#room_logs).
- 🔗 branch_id_2 <!-- orbit link: "#branch_links", status: "foreign key" -->
  > `author_links_2.branch_id_2` · `int8` · required
  >
  > Points at [`branch_links`](#branch_links).
- 🔤 card_amount
  > `author_links_2.card_amount` · `varchar` · optional
- 🧩 event_title
  > `author_links_2.event_title` · `jsonb` · required
- 🧩 book_public
  > `author_links_2.book_public` · `jsonb` · required
- 🔗 book_id <!-- orbit link: "#book_links", status: "foreign key" -->
  > `author_links_2.book_id` · `int8` · required
  >
  > Points at [`book_links`](#book_links).
- 🔗 rooms_id <!-- orbit link: "#rooms", status: "foreign key" -->
  > `author_links_2.rooms_id` · `int8` · required
  >
  > Points at [`rooms`](#rooms).
- 🕒 shelf_amount
  > `author_links_2.shelf_amount` · `timestamp` · required
- 🕒 event_priority
  > `author_links_2.event_priority` · `timestamp` · required
- 🔤 member_archived
  > `author_links_2.member_archived` · `varchar` · required
- 🔗 card_id <!-- orbit link: "#card_queues", status: "foreign key" -->
  > `author_links_2.card_id` · `int8` · required
  >
  > Points at [`card_queues`](#card_queues).
- 🔢 event_reviewed
  > `author_links_2.event_reviewed` · `int4` · optional
- 🔤 series_active
  > `author_links_2.series_active` · `varchar` · required
- 🧩 budget_ended
  > `author_links_2.budget_ended` · `jsonb` · optional
- 🔗 order_id_3 <!-- orbit link: "#order_links", status: "foreign key" -->
  > `author_links_2.order_id_3` · `int8` · required
  >
  > Points at [`order_links`](#order_links).
- 🔤 review_ended
  > `author_links_2.review_ended` · `varchar` · optional
- 🕒 notice_reviewed
  > `author_links_2.notice_reviewed` · `timestamp` · optional
- 🔗 order_id_4 <!-- orbit link: "#order_links", status: "foreign key" -->
  > `author_links_2.order_id_4` · `int8` · required
  >
  > Points at [`order_links`](#order_links).
- ☑️ tag_priority
  > `author_links_2.tag_priority` · `bool` · optional
- 🕒 budget_count_2
  > `author_links_2.budget_count_2` · `timestamp` · optional
- 🔢 author_title_2
  > `author_links_2.author_title_2` · `int4` · optional
- ☑️ loan_public
  > `author_links_2.loan_public` · `bool` · optional
- 🔗 copy_id_2 <!-- orbit link: "#copy_counts", status: "foreign key" -->
  > `author_links_2.copy_id_2` · `int8` · required
  >
  > Points at [`copy_counts`](#copy_counts).
- 🧩 loan_region
  > `author_links_2.loan_region` · `jsonb` · optional
- ☑️ loan_active
  > `author_links_2.loan_active` · `bool` · optional
- 🔤 book_reviewed
  > `author_links_2.book_reviewed` · `varchar` · optional
- 🧩 member_format
  > `author_links_2.member_format` · `jsonb` · optional
- 🕒 report_status
  > `author_links_2.report_status` · `timestamp` · optional
- ☑️ branch_format
  > `author_links_2.branch_format` · `bool` · required
- 🔢 hold_archived
  > `author_links_2.hold_archived` · `int4` · required
- 🔗 ebook_id_4 <!-- orbit link: "#ebook_logs", status: "foreign key" -->
  > `author_links_2.ebook_id_4` · `int8` · required
  >
  > Points at [`ebook_logs`](#ebook_logs).
- 🔢 hold_position
  > `author_links_2.hold_position` · `int4` · required
- 🕒 role_updated
  > `author_links_2.role_updated` · `timestamp` · required
- 🔢 event_notes
  > `author_links_2.event_notes` · `int4` · optional
- 🧩 hold_priority
  > `author_links_2.hold_priority` · `jsonb` · optional
- 🧩 notice_reviewed_2
  > `author_links_2.notice_reviewed_2` · `jsonb` · required
- 🔢 report_state
  > `author_links_2.report_state` · `int4` · optional
- ☑️ review_source
  > `author_links_2.review_source` · `bool` · optional
- 🔢 budget_public
  > `author_links_2.budget_public` · `int4` · required
- 🔢 device_kind
  > `author_links_2.device_kind` · `int4` · optional
- 🔤 event_amount
  > `author_links_2.event_amount` · `varchar` · required
- 🔤 payment_external
  > `author_links_2.payment_external` · `varchar` · required
- ☑️ report_notes
  > `author_links_2.report_notes` · `bool` · optional
- ☑️ tag_position
  > `author_links_2.tag_position` · `bool` · optional
- ☑️ room_priority
  > `author_links_2.room_priority` · `bool` · required
- 🕒 shelf_count
  > `author_links_2.shelf_count` · `timestamp` · required
- ☑️ review_priority
  > `author_links_2.review_priority` · `bool` · required
- 🔗 notice_id_2 <!-- orbit link: "#notice_counts", status: "foreign key" -->
  > `author_links_2.notice_id_2` · `int8` · required
  >
  > Points at [`notice_counts`](#notice_counts).
- ☑️ payment_region
  > `author_links_2.payment_region` · `bool` · required
- ☑️ book_updated
  > `author_links_2.book_updated` · `bool` · optional
- ☑️ review_status
  > `author_links_2.review_status` · `bool` · required
- 🔗 review_id_5 <!-- orbit link: "#review_logs", status: "foreign key" -->
  > `author_links_2.review_id_5` · `int8` · required
  >
  > Points at [`review_logs`](#review_logs).
- 🔗 notice_id_3 <!-- orbit link: "#notice_counts", status: "foreign key" -->
  > `author_links_2.notice_id_3` · `int8` · required
  >
  > Points at [`notice_counts`](#notice_counts).
- 🔗 member_id <!-- orbit link: "#member_history", status: "foreign key" -->
  > `author_links_2.member_id` · `int8` · required
  >
  > Points at [`member_history`](#member_history).
- 🧩 notice_region
  > `author_links_2.notice_region` · `jsonb` · required
- 🔢 payment_region_2
  > `author_links_2.payment_region_2` · `int4` · required
- ☑️ vendor_kind
  > `author_links_2.vendor_kind` · `bool` · optional
- 🧩 staff_public
  > `author_links_2.staff_public` · `jsonb` · required
- 🧩 shelf_started
  > `author_links_2.shelf_started` · `jsonb` · optional
- 🔢 fee_format
  > `author_links_2.fee_format` · `int4` · optional
- 🔤 visit_label
  > `author_links_2.visit_label` · `varchar` · optional
- 🧩 room_name
  > `author_links_2.room_name` · `jsonb` · required
- 🧩 ebook_code
  > `author_links_2.ebook_code` · `jsonb` · required
- 🕒 ebook_count_2
  > `author_links_2.ebook_count_2` · `timestamp` · optional
- ☑️ fee_kind
  > `author_links_2.fee_kind` · `bool` · optional
- 🔢 role_status
  > `author_links_2.role_status` · `int4` · optional
- 🔢 license_archived
  > `author_links_2.license_archived` · `int4` · required
- ☑️ copy_amount
  > `author_links_2.copy_amount` · `bool` · optional
- 🔗 event_id_2 <!-- orbit link: "#event_history", status: "foreign key" -->
  > `author_links_2.event_id_2` · `int8` · required
  >
  > Points at [`event_history`](#event_history).
- ☑️ ebook_updated
  > `author_links_2.ebook_updated` · `bool` · optional
- 🔢 card_status
  > `author_links_2.card_status` · `int4` · required
- 🧩 tag_archived
  > `author_links_2.tag_archived` · `jsonb` · required
- 🔤 notice_code
  > `author_links_2.notice_code` · `varchar` · optional
- 🔤 review_code
  > `author_links_2.review_code` · `varchar` · optional
- 🧩 card_code
  > `author_links_2.card_code` · `jsonb` · required
- 🔤 book_active
  > `author_links_2.book_active` · `varchar` · required
- 🔢 license_started
  > `author_links_2.license_started` · `int4` · required
- 🔗 device_id <!-- orbit link: "#device_settings", status: "foreign key" -->
  > `author_links_2.device_id` · `int8` · required
  >
  > Points at [`device_settings`](#device_settings).
- 🧩 report_language
  > `author_links_2.report_language` · `jsonb` · optional
- 🔢 notice_amount
  > `author_links_2.notice_amount` · `int4` · required
- 🧩 event_source
  > `author_links_2.event_source` · `jsonb` · required
- 🧩 fee_position
  > `author_links_2.fee_position` · `jsonb` · required
- 🔤 member_public
  > `author_links_2.member_public` · `varchar` · optional
- 🧩 notice_archived
  > `author_links_2.notice_archived` · `jsonb` · required
- 🧩 copy_kind
  > `author_links_2.copy_kind` · `jsonb` · required
- 🔢 order_active
  > `author_links_2.order_active` · `int4` · optional
- 🧩 license_title
  > `author_links_2.license_title` · `jsonb` · optional
- 🔤 event_name
  > `author_links_2.event_name` · `varchar` · optional
- ☑️ vendor_source
  > `author_links_2.vendor_source` · `bool` · required
- 🔢 copy_name
  > `author_links_2.copy_name` · `int4` · optional
- 🔗 visits_id_2 <!-- orbit link: "#visits", status: "foreign key" -->
  > `author_links_2.visits_id_2` · `int8` · required
  >
  > Points at [`visits`](#visits).
- 🔢 order_title
  > `author_links_2.order_title` · `int4` · optional
- ☑️ room_created
  > `author_links_2.room_created` · `bool` · optional
- 🕒 branch_language
  > `author_links_2.branch_language` · `timestamp` · optional
- 🕒 report_reviewed
  > `author_links_2.report_reviewed` · `timestamp` · required
- 🔤 hold_region
  > `author_links_2.hold_region` · `varchar` · optional
- 🕒 series_total
  > `author_links_2.series_total` · `timestamp` · required
- ☑️ report_notes_2
  > `author_links_2.report_notes_2` · `bool` · optional
- ☑️ payment_code
  > `author_links_2.payment_code` · `bool` · optional
- 🔗 donation_id <!-- orbit link: "#donation_history", status: "foreign key" -->
  > `author_links_2.donation_id` · `int8` · required
  >
  > Points at [`donation_history`](#donation_history).
- 🔤 payment_language
  > `author_links_2.payment_language` · `varchar` · optional
- ☑️ member_format_2
  > `author_links_2.member_format_2` · `bool` · required
- 🔤 order_external
  > `author_links_2.order_external` · `varchar` · required
- 🕒 review_priority_2
  > `author_links_2.review_priority_2` · `timestamp` · optional
- ☑️ fee_status
  > `author_links_2.fee_status` · `bool` · required
- ☑️ series_status_3
  > `author_links_2.series_status_3` · `bool` · required
- 🕒 card_status_2
  > `author_links_2.card_status_2` · `timestamp` · optional
- 🕒 staff_active
  > `author_links_2.staff_active` · `timestamp` · required
- 🕒 series_language
  > `author_links_2.series_language` · `timestamp` · optional
- 🔤 card_total
  > `author_links_2.card_total` · `varchar` · required
- 🧩 ebook_language
  > `author_links_2.ebook_language` · `jsonb` · optional
- 🔗 audit_id <!-- orbit link: "#audit_links", status: "foreign key" -->
  > `author_links_2.audit_id` · `int8` · required
  >
  > Points at [`audit_links`](#audit_links).
- 🔤 series_status_4
  > `author_links_2.series_status_4` · `varchar` · required
- 🔤 ebook_priority
  > `author_links_2.ebook_priority` · `varchar` · optional
- 🔗 license_id_3 <!-- orbit link: "#license_versions", status: "foreign key" -->
  > `author_links_2.license_id_3` · `int8` · required
  >
  > Points at [`license_versions`](#license_versions).
- 🕒 tag_count
  > `author_links_2.tag_count` · `timestamp` · required
- 🔤 tag_public
  > `author_links_2.tag_public` · `varchar` · required
- 🕒 order_status
  > `author_links_2.order_status` · `timestamp` · optional
- 🔗 author_id <!-- orbit link: "#author_links_2", status: "foreign key" -->
  > `author_links_2.author_id` · `int8` · required
  >
  > Points at [`author_links_2`](#author_links_2).
- 🧩 author_total
  > `author_links_2.author_total` · `jsonb` · required
- 🔢 order_label
  > `author_links_2.order_label` · `int4` · required
- 🔢 payment_updated
  > `author_links_2.payment_updated` · `int4` · required
- ☑️ branch_code
  > `author_links_2.branch_code` · `bool` · required
- ☑️ repair_created
  > `author_links_2.repair_created` · `bool` · optional
- 🕒 report_started
  > `author_links_2.report_started` · `timestamp` · required
- 🔢 author_status
  > `author_links_2.author_status` · `int4` · required
- 🔤 member_kind
  > `author_links_2.member_kind` · `varchar` · required
- 🧩 visit_ended
  > `author_links_2.visit_ended` · `jsonb` · optional
- 🧩 role_status_2
  > `author_links_2.role_status_2` · `jsonb` · required
- 🧩 staff_amount
  > `author_links_2.staff_amount` · `jsonb` · optional
- 🔢 hold_public
  > `author_links_2.hold_public` · `int4` · required
- 🧩 notice_title
  > `author_links_2.notice_title` · `jsonb` · required
- 🔗 budget_id <!-- orbit link: "#budget_queues", status: "foreign key" -->
  > `author_links_2.budget_id` · `int8` · required
  >
  > Points at [`budget_queues`](#budget_queues).
- 🔤 visit_format
  > `author_links_2.visit_format` · `varchar` · optional
- 🧩 visit_region
  > `author_links_2.visit_region` · `jsonb` · required
- ☑️ notice_archived_2
  > `author_links_2.notice_archived_2` · `bool` · optional
- 🔤 event_status
  > `author_links_2.event_status` · `varchar` · required
- 🔢 fee_name
  > `author_links_2.fee_name` · `int4` · required
- 🔗 licenses_id <!-- orbit link: "#licenses", status: "foreign key" -->
  > `author_links_2.licenses_id` · `int8` · required
  >
  > Points at [`licenses`](#licenses).
- 🔤 review_label
  > `author_links_2.review_label` · `varchar` · optional
- 🕒 series_reviewed
  > `author_links_2.series_reviewed` · `timestamp` · required
- 🕒 event_reviewed_2
  > `author_links_2.event_reviewed_2` · `timestamp` · optional
- 🔗 budget_id_2 <!-- orbit link: "#budget_settings_2", status: "foreign key" -->
  > `author_links_2.budget_id_2` · `int8` · required
  >
  > Points at [`budget_settings_2`](#budget_settings_2).
- 🕒 ebook_created
  > `author_links_2.ebook_created` · `timestamp` · optional
- 🔗 audit_id_2 <!-- orbit link: "#audit_settings", status: "foreign key" -->
  > `author_links_2.audit_id_2` · `int8` · required
  >
  > Points at [`audit_settings`](#audit_settings).
- 🔤 author_created
  > `author_links_2.author_created` · `varchar` · required
- 🔤 event_external
  > `author_links_2.event_external` · `varchar` · required
- 🔤 series_updated
  > `author_links_2.series_updated` · `varchar` · optional
- 🔤 loan_created
  > `author_links_2.loan_created` · `varchar` · required
- 🧩 role_status_3
  > `author_links_2.role_status_3` · `jsonb` · required
- 🧩 book_started
  > `author_links_2.book_started` · `jsonb` · required
- 🧩 role_active_2
  > `author_links_2.role_active_2` · `jsonb` · required
- 🕒 order_reviewed
  > `author_links_2.order_reviewed` · `timestamp` · optional
- 🔤 card_reviewed
  > `author_links_2.card_reviewed` · `varchar` · optional
- ☑️ event_position_2
  > `author_links_2.event_position_2` · `bool` · optional
- 🔢 review_label_2
  > `author_links_2.review_label_2` · `int4` · optional
- 🔤 role_format
  > `author_links_2.role_format` · `varchar` · required
- 🕒 payment_region_3
  > `author_links_2.payment_region_3` · `timestamp` · required
- 🕒 ebook_position
  > `author_links_2.ebook_position` · `timestamp` · required
- 🧩 tag_archived_2
  > `author_links_2.tag_archived_2` · `jsonb` · required
- 🧩 shelf_external
  > `author_links_2.shelf_external` · `jsonb` · required
- 🔢 report_region
  > `author_links_2.report_region` · `int4` · optional
- ☑️ vendor_label
  > `author_links_2.vendor_label` · `bool` · required

### shelf_queues
<!-- orbit
id: "shelf_queues"
icon: "📇"
collapsed: true
-->
> `shelf_queues` · table in Events
>
> One row per shelf, with 21 columns and about 806 thousand rows.
>
> **Belongs to** [`visit_queues`](#visit_queues) · [`book_items`](#book_items)

- 🔑 id <!-- orbit status: "primary key" -->
  > `shelf_queues.id` · `int8` · required
- ☑️ loan_ended
  > `shelf_queues.loan_ended` · `bool` · optional
- 🕒 event_active
  > `shelf_queues.event_active` · `timestamp` · optional
- 🔗 report_id <!-- orbit link: "#report_logs", status: "foreign key" -->
  > `shelf_queues.report_id` · `int8` · required
  >
  > Points at [`report_logs`](#report_logs).
- 🔗 donation_id <!-- orbit link: "#donation_queues", status: "foreign key" -->
  > `shelf_queues.donation_id` · `int8` · required
  >
  > Points at [`donation_queues`](#donation_queues).
- 🕒 copy_code
  > `shelf_queues.copy_code` · `timestamp` · required
- 🔗 report_id_2 <!-- orbit link: "#report_queues", status: "foreign key" -->
  > `shelf_queues.report_id_2` · `int8` · required
  >
  > Points at [`report_queues`](#report_queues).
- 🔢 series_reviewed
  > `shelf_queues.series_reviewed` · `int4` · required
- ☑️ donation_active
  > `shelf_queues.donation_active` · `bool` · required
- 🔤 review_kind
  > `shelf_queues.review_kind` · `varchar` · required
- ☑️ shelf_priority
  > `shelf_queues.shelf_priority` · `bool` · required
- 🕒 payment_status
  > `shelf_queues.payment_status` · `timestamp` · optional
- ☑️ order_language
  > `shelf_queues.order_language` · `bool` · required
- 🔗 staff_id <!-- orbit link: "#staff_counts", status: "foreign key" -->
  > `shelf_queues.staff_id` · `int8` · required
  >
  > Points at [`staff_counts`](#staff_counts).
- 🔗 loan_id <!-- orbit link: "#loan_settings", status: "foreign key" -->
  > `shelf_queues.loan_id` · `int8` · required
  >
  > Points at [`loan_settings`](#loan_settings).
- 🧩 member_external
  > `shelf_queues.member_external` · `jsonb` · optional
- 🔤 event_name
  > `shelf_queues.event_name` · `varchar` · required
- 🔗 fee_id <!-- orbit link: "#fee_requests", status: "foreign key" -->
  > `shelf_queues.fee_id` · `int8` · required
  >
  > Points at [`fee_requests`](#fee_requests).
- 🧩 hold_name
  > `shelf_queues.hold_name` · `jsonb` · optional
- 🔤 copy_started
  > `shelf_queues.copy_started` · `varchar` · optional
- 🔗 copy_id <!-- orbit link: "#copy_items", status: "foreign key" -->
  > `shelf_queues.copy_id` · `int8` · required
  >
  > Points at [`copy_items`](#copy_items).

### fee_requests
<!-- orbit
id: "fee_requests"
icon: "📗"
collapsed: true
-->
> `fee_requests` · table in Events
>
> One row per fee, with 23 columns and about 202 thousand rows.
>
> **Belongs to** [`loan_counts`](#loan_counts) · [`review_queues`](#review_queues)

- 🔑 id <!-- orbit status: "primary key" -->
  > `fee_requests.id` · `int8` · required
- 🔢 tag_external
  > `fee_requests.tag_external` · `int4` · required
- 🔗 license_id <!-- orbit link: "#license_queues", status: "foreign key" -->
  > `fee_requests.license_id` · `int8` · required
  >
  > Points at [`license_queues`](#license_queues).
- 🔤 license_state
  > `fee_requests.license_state` · `varchar` · required
- 🧩 hold_active
  > `fee_requests.hold_active` · `jsonb` · required
- ☑️ event_archived
  > `fee_requests.event_archived` · `bool` · required
- 🔢 room_title
  > `fee_requests.room_title` · `int4` · required
- 🕒 payment_position
  > `fee_requests.payment_position` · `timestamp` · optional
- 🕒 repair_code
  > `fee_requests.repair_code` · `timestamp` · optional
- 🔗 payment_id <!-- orbit link: "#payment_settings", status: "foreign key" -->
  > `fee_requests.payment_id` · `int8` · required
  >
  > Points at [`payment_settings`](#payment_settings).
- 🧩 donation_label
  > `fee_requests.donation_label` · `jsonb` · required
- 🧩 loan_public
  > `fee_requests.loan_public` · `jsonb` · optional
- 🕒 payment_public
  > `fee_requests.payment_public` · `timestamp` · required
- 🕒 shelf_count
  > `fee_requests.shelf_count` · `timestamp` · optional
- 🔗 series_id <!-- orbit link: "#series_history", status: "foreign key" -->
  > `fee_requests.series_id` · `int8` · required
  >
  > Points at [`series_history`](#series_history).
- ☑️ tag_priority
  > `fee_requests.tag_priority` · `bool` · optional
- ☑️ report_region
  > `fee_requests.report_region` · `bool` · required
- 🔗 vendor_id <!-- orbit link: "#vendor_items", status: "foreign key" -->
  > `fee_requests.vendor_id` · `int8` · required
  >
  > Points at [`vendor_items`](#vendor_items).
- ☑️ repair_region
  > `fee_requests.repair_region` · `bool` · optional
- 🔢 card_notes
  > `fee_requests.card_notes` · `int4` · required
- 🔤 license_status
  > `fee_requests.license_status` · `varchar` · optional
- 🔗 repair_id <!-- orbit link: "#repair_requests", status: "foreign key" -->
  > `fee_requests.repair_id` · `int8` · required
  >
  > Points at [`repair_requests`](#repair_requests).
- 🧩 room_active
  > `fee_requests.room_active` · `jsonb` · optional

### event_history
<!-- orbit
id: "event_history"
icon: "📇"
collapsed: true
-->
> `event_history` · table in Events
>
> One row per event, with 21 columns and about 561 thousand rows.
>
> **Belongs to** [`card_links_2`](#card_links_2)

- 🔑 id <!-- orbit status: "primary key" -->
  > `event_history.id` · `int8` · required
- 🕒 device_title
  > `event_history.device_title` · `timestamp` · optional
- ☑️ author_kind
  > `event_history.author_kind` · `bool` · optional
- ☑️ review_reviewed
  > `event_history.review_reviewed` · `bool` · required
- 🕒 author_status
  > `event_history.author_status` · `timestamp` · optional
- 🧩 ebook_amount
  > `event_history.ebook_amount` · `jsonb` · required
- 🔢 notice_format
  > `event_history.notice_format` · `int4` · required
- ☑️ branch_reviewed
  > `event_history.branch_reviewed` · `bool` · optional
- 🧩 role_reviewed
  > `event_history.role_reviewed` · `jsonb` · required
- 🔤 role_external
  > `event_history.role_external` · `varchar` · required
- 🔢 branch_label
  > `event_history.branch_label` · `int4` · required
- 🔗 series_id <!-- orbit link: "#series_history", status: "foreign key" -->
  > `event_history.series_id` · `int8` · required
  >
  > Points at [`series_history`](#series_history).
- 🔗 copy_id <!-- orbit link: "#copy_items", status: "foreign key" -->
  > `event_history.copy_id` · `int8` · required
  >
  > Points at [`copy_items`](#copy_items).
- 🔗 shelfs_id <!-- orbit link: "#shelfs", status: "foreign key" -->
  > `event_history.shelfs_id` · `int8` · required
  >
  > Points at [`shelfs`](#shelfs).
- 🔢 notice_source
  > `event_history.notice_source` · `int4` · required
- 🔤 book_source
  > `event_history.book_source` · `varchar` · optional
- 🧩 repair_position
  > `event_history.repair_position` · `jsonb` · required
- 🔢 payment_priority
  > `event_history.payment_priority` · `int4` · optional
- 🔤 copy_language
  > `event_history.copy_language` · `varchar` · optional
- 🕒 staff_format
  > `event_history.staff_format` · `timestamp` · optional
- 🔤 audit_external
  > `event_history.audit_external` · `varchar` · required

### audit_links_3
<!-- orbit
id: "audit_links_3"
icon: "🧮"
collapsed: true
-->
> `audit_links_3` · table in Events
>
> One row per audit, with 13 columns and about 462 thousand rows.
>
> **Belongs to** [`holds`](#holds) · [`report_queues_2`](#report_queues_2) · [`visit_items`](#visit_items)

- 🔑 id <!-- orbit status: "primary key" -->
  > `audit_links_3.id` · `int8` · required
- 🧩 book_source
  > `audit_links_3.book_source` · `jsonb` · required
- ☑️ card_kind
  > `audit_links_3.card_kind` · `bool` · required
- 🔢 loan_external
  > `audit_links_3.loan_external` · `int4` · optional
- 🔢 author_title
  > `audit_links_3.author_title` · `int4` · required
- 🕒 vendor_amount
  > `audit_links_3.vendor_amount` · `timestamp` · optional
- ☑️ event_title
  > `audit_links_3.event_title` · `bool` · required
- ☑️ fee_source
  > `audit_links_3.fee_source` · `bool` · required
- 🔗 ebook_id <!-- orbit link: "#ebook_queues", status: "foreign key" -->
  > `audit_links_3.ebook_id` · `int8` · required
  >
  > Points at [`ebook_queues`](#ebook_queues).
- 🕒 review_kind
  > `audit_links_3.review_kind` · `timestamp` · required
- 🧩 shelf_active
  > `audit_links_3.shelf_active` · `jsonb` · required
- 🕒 hold_code
  > `audit_links_3.hold_code` · `timestamp` · optional
- 🔢 staff_ended
  > `audit_links_3.staff_ended` · `int4` · optional

### report_logs_2
<!-- orbit
id: "report_logs_2"
icon: "📗"
collapsed: true
-->
> `report_logs_2` · table in Events
>
> One row per report, with 22 columns and about 284 thousand rows.
>
> **Belongs to** [`ebook_requests`](#ebook_requests) · [`staff_settings`](#staff_settings) · [`loan_history`](#loan_history) · [`author_links_2`](#author_links_2)

- 🔑 id <!-- orbit status: "primary key" -->
  > `report_logs_2.id` · `int8` · required
- 🕒 book_kind
  > `report_logs_2.book_kind` · `timestamp` · optional
- 🔗 visit_id <!-- orbit link: "#visit_links", status: "foreign key" -->
  > `report_logs_2.visit_id` · `int8` · required
  >
  > Points at [`visit_links`](#visit_links).
- 🕒 review_label
  > `report_logs_2.review_label` · `timestamp` · required
- 🧩 loan_active
  > `report_logs_2.loan_active` · `jsonb` · optional
- 🔗 author_id <!-- orbit link: "#author_links_2", status: "foreign key" -->
  > `report_logs_2.author_id` · `int8` · required
  >
  > Points at [`author_links_2`](#author_links_2).
- 🔤 fee_public
  > `report_logs_2.fee_public` · `varchar` · required
- 🔗 branchs_id <!-- orbit link: "#branchs", status: "foreign key" -->
  > `report_logs_2.branchs_id` · `int8` · required
  >
  > Points at [`branchs`](#branchs).
- 🧩 ebook_position
  > `report_logs_2.ebook_position` · `jsonb` · required
- ☑️ review_started
  > `report_logs_2.review_started` · `bool` · required
- 🧩 hold_format
  > `report_logs_2.hold_format` · `jsonb` · required
- 🔢 budget_region
  > `report_logs_2.budget_region` · `int4` · optional
- 🔢 staff_name
  > `report_logs_2.staff_name` · `int4` · required
- 🕒 branch_ended
  > `report_logs_2.branch_ended` · `timestamp` · optional
- 🔢 payment_archived
  > `report_logs_2.payment_archived` · `int4` · required
- 🕒 fee_reviewed
  > `report_logs_2.fee_reviewed` · `timestamp` · optional
- 🕒 branch_status
  > `report_logs_2.branch_status` · `timestamp` · required
- 🕒 payment_amount
  > `report_logs_2.payment_amount` · `timestamp` · required
- ☑️ branch_public
  > `report_logs_2.branch_public` · `bool` · required
- 🔗 shelf_id <!-- orbit link: "#shelf_counts", status: "foreign key" -->
  > `report_logs_2.shelf_id` · `int8` · required
  >
  > Points at [`shelf_counts`](#shelf_counts).
- ☑️ device_total
  > `report_logs_2.device_total` · `bool` · required
- 🔗 vendor_id <!-- orbit link: "#vendor_items", status: "foreign key" -->
  > `report_logs_2.vendor_id` · `int8` · required
  >
  > Points at [`vendor_items`](#vendor_items).

### audit_queues
<!-- orbit
id: "audit_queues"
icon: "🗃️"
collapsed: true
-->
> `audit_queues` · table in Events
>
> One row per audit, with 14 columns and about 478 thousand rows.
>
> **Belongs to** [`report_items`](#report_items) · [`notice_counts_2`](#notice_counts_2) · [`loan_history`](#loan_history)

- 🔑 id <!-- orbit status: "primary key" -->
  > `audit_queues.id` · `int8` · required
- 🕒 branch_name
  > `audit_queues.branch_name` · `timestamp` · required
- 🔗 loan_id <!-- orbit link: "#loan_counts", status: "foreign key" -->
  > `audit_queues.loan_id` · `int8` · required
  >
  > Points at [`loan_counts`](#loan_counts).
- 🔗 budget_id <!-- orbit link: "#budget_queues", status: "foreign key" -->
  > `audit_queues.budget_id` · `int8` · required
  >
  > Points at [`budget_queues`](#budget_queues).
- 🧩 device_code
  > `audit_queues.device_code` · `jsonb` · required
- ☑️ fee_amount
  > `audit_queues.fee_amount` · `bool` · optional
- ☑️ author_code
  > `audit_queues.author_code` · `bool` · optional
- 🔢 notice_public
  > `audit_queues.notice_public` · `int4` · optional
- ☑️ staff_started
  > `audit_queues.staff_started` · `bool` · required
- 🕒 card_source
  > `audit_queues.card_source` · `timestamp` · required
- 🧩 role_created
  > `audit_queues.role_created` · `jsonb` · required
- 🧩 notice_amount
  > `audit_queues.notice_amount` · `jsonb` · optional
- 🔗 event_id <!-- orbit link: "#event_queues", status: "foreign key" -->
  > `audit_queues.event_id` · `int8` · required
  >
  > Points at [`event_queues`](#event_queues).
- 🔢 branch_count
  > `audit_queues.branch_count` · `int4` · required

### ebook_versions
<!-- orbit
id: "ebook_versions"
icon: "🧮"
collapsed: true
-->
> `ebook_versions` · table in Events
>
> One row per ebook, with 15 columns and about 874 thousand rows.
>
> **Belongs to** [`visit_items`](#visit_items) · [`order_versions`](#order_versions) · [`device_settings`](#device_settings)

- 🔑 id <!-- orbit status: "primary key" -->
  > `ebook_versions.id` · `int8` · required
- 🔢 series_external
  > `ebook_versions.series_external` · `int4` · required
- ☑️ review_priority
  > `ebook_versions.review_priority` · `bool` · required
- ☑️ role_updated
  > `ebook_versions.role_updated` · `bool` · optional
- 🔢 vendor_priority
  > `ebook_versions.vendor_priority` · `int4` · required
- 🕒 copy_state
  > `ebook_versions.copy_state` · `timestamp` · optional
- ☑️ audit_amount
  > `ebook_versions.audit_amount` · `bool` · optional
- 🔗 staff_id <!-- orbit link: "#staff_counts", status: "foreign key" -->
  > `ebook_versions.staff_id` · `int8` · required
  >
  > Points at [`staff_counts`](#staff_counts).
- 🔗 loan_id <!-- orbit link: "#loan_logs_2", status: "foreign key" -->
  > `ebook_versions.loan_id` · `int8` · required
  >
  > Points at [`loan_logs_2`](#loan_logs_2).
- 🧩 vendor_label
  > `ebook_versions.vendor_label` · `jsonb` · optional
- 🕒 donation_amount
  > `ebook_versions.donation_amount` · `timestamp` · optional
- 🔗 book_id <!-- orbit link: "#book_items", status: "foreign key" -->
  > `ebook_versions.book_id` · `int8` · required
  >
  > Points at [`book_items`](#book_items).
- 🔢 payment_kind
  > `ebook_versions.payment_kind` · `int4` · optional
- 🔢 role_status
  > `ebook_versions.role_status` · `int4` · optional
- 🧩 review_state
  > `ebook_versions.review_state` · `jsonb` · optional

### loan_settings
<!-- orbit
id: "loan_settings"
icon: "🧷"
collapsed: true
-->
> `loan_settings` · table in Events
>
> One row per loan, with 12 columns and about 314 thousand rows.
>
> **Belongs to** [`role_counts`](#role_counts)

- 🔑 id <!-- orbit status: "primary key" -->
  > `loan_settings.id` · `int8` · required
- 🕒 card_name
  > `loan_settings.card_name` · `timestamp` · required
- 🔗 order_id <!-- orbit link: "#order_logs", status: "foreign key" -->
  > `loan_settings.order_id` · `int8` · required
  >
  > Points at [`order_logs`](#order_logs).
- 🧩 staff_notes
  > `loan_settings.staff_notes` · `jsonb` · optional
- ☑️ license_started
  > `loan_settings.license_started` · `bool` · required
- 🕒 shelf_total
  > `loan_settings.shelf_total` · `timestamp` · required
- 🔢 book_amount
  > `loan_settings.book_amount` · `int4` · required
- ☑️ staff_region
  > `loan_settings.staff_region` · `bool` · required
- 🧩 review_ended
  > `loan_settings.review_ended` · `jsonb` · required
- 🧩 fee_reviewed
  > `loan_settings.fee_reviewed` · `jsonb` · required
- 🔗 visit_id <!-- orbit link: "#visit_links", status: "foreign key" -->
  > `loan_settings.visit_id` · `int8` · required
  >
  > Points at [`visit_links`](#visit_links).
- 🕒 copy_region
  > `loan_settings.copy_region` · `timestamp` · optional

### tag_requests
<!-- orbit
id: "tag_requests"
icon: "🧮"
collapsed: true
-->
> `tag_requests` · table in Events
>
> One row per tag, with 10 columns and about 466 thousand rows.
>
> **Belongs to** [`staff_settings`](#staff_settings) · [`author_links`](#author_links) · [`budget_settings_2`](#budget_settings_2) · [`ebook_queues`](#ebook_queues)

- 🔑 id <!-- orbit status: "primary key" -->
  > `tag_requests.id` · `int8` · required
- 🔤 license_archived
  > `tag_requests.license_archived` · `varchar` · required
- 🔤 order_state
  > `tag_requests.order_state` · `varchar` · required
- ☑️ tag_label
  > `tag_requests.tag_label` · `bool` · optional
- 🕒 device_language
  > `tag_requests.device_language` · `timestamp` · optional
- 🕒 book_started
  > `tag_requests.book_started` · `timestamp` · required
- 🔢 event_priority
  > `tag_requests.event_priority` · `int4` · required
- ☑️ vendor_total
  > `tag_requests.vendor_total` · `bool` · optional
- 🔢 card_count
  > `tag_requests.card_count` · `int4` · optional
- 🧩 role_notes
  > `tag_requests.role_notes` · `jsonb` · required

## 💳 Fees & Payments
<!-- orbit
id: "area-fees-payments"
color: "#D97706"
-->
> 13 tables. Largest: [`license_versions`](#license_versions), [`member_counts_2`](#member_counts_2), [`review_versions`](#review_versions).
>
> **Linked to** [`license_versions`](#license_versions) · [`room_logs`](#room_logs) · [`audit_links`](#audit_links)

### license_versions
<!-- orbit
id: "license_versions"
icon: "📗"
collapsed: true
-->
> `license_versions` · table in Fees & Payments
>
> One row per license, with 10 columns and about 440 thousand rows.
>
> **Belongs to** [`review_logs`](#review_logs) · [`staff_counts`](#staff_counts)

- 🔑 id <!-- orbit status: "primary key" -->
  > `license_versions.id` · `int8` · required
- 🔢 card_public
  > `license_versions.card_public` · `int4` · required
- 🔗 holds_id <!-- orbit link: "#holds_2", status: "foreign key" -->
  > `license_versions.holds_id` · `int8` · required
  >
  > Points at [`holds_2`](#holds_2).
- 🔢 visit_archived
  > `license_versions.visit_archived` · `int4` · required
- 🔢 copy_title
  > `license_versions.copy_title` · `int4` · required
- ☑️ room_language
  > `license_versions.room_language` · `bool` · required
- 🔗 license_id <!-- orbit link: "#license_logs_2", status: "foreign key" -->
  > `license_versions.license_id` · `int8` · required
  >
  > Points at [`license_logs_2`](#license_logs_2).
- 🧩 series_priority
  > `license_versions.series_priority` · `jsonb` · required
- 🔢 event_started
  > `license_versions.event_started` · `int4` · required
- 🔤 shelf_amount
  > `license_versions.shelf_amount` · `varchar` · optional

### member_counts_2
<!-- orbit
id: "member_counts_2"
icon: "📇"
collapsed: true
-->
> `member_counts_2` · table in Fees & Payments
>
> One row per member, with 23 columns and about 525 thousand rows.
>
> **Belongs to** [`hold_history`](#hold_history) · [`fee_history`](#fee_history)

- 🔑 id <!-- orbit status: "primary key" -->
  > `member_counts_2.id` · `int8` · required
- 🕒 donation_archived
  > `member_counts_2.donation_archived` · `timestamp` · required
- 🔤 author_external
  > `member_counts_2.author_external` · `varchar` · optional
- 🕒 review_count
  > `member_counts_2.review_count` · `timestamp` · required
- 🔤 book_code
  > `member_counts_2.book_code` · `varchar` · required
- 🧩 order_kind
  > `member_counts_2.order_kind` · `jsonb` · required
- 🔤 vendor_external
  > `member_counts_2.vendor_external` · `varchar` · optional
- 🕒 shelf_reviewed
  > `member_counts_2.shelf_reviewed` · `timestamp` · optional
- 🔗 device_id <!-- orbit link: "#device_items", status: "foreign key" -->
  > `member_counts_2.device_id` · `int8` · required
  >
  > Points at [`device_items`](#device_items).
- ☑️ order_started
  > `member_counts_2.order_started` · `bool` · required
- 🔗 ebook_id <!-- orbit link: "#ebook_queues", status: "foreign key" -->
  > `member_counts_2.ebook_id` · `int8` · required
  >
  > Points at [`ebook_queues`](#ebook_queues).
- 🔤 vendor_code
  > `member_counts_2.vendor_code` · `varchar` · required
- ☑️ audit_amount
  > `member_counts_2.audit_amount` · `bool` · optional
- 🔗 staff_id <!-- orbit link: "#staff_logs", status: "foreign key" -->
  > `member_counts_2.staff_id` · `int8` · required
  >
  > Points at [`staff_logs`](#staff_logs).
- 🔗 loan_id <!-- orbit link: "#loan_counts", status: "foreign key" -->
  > `member_counts_2.loan_id` · `int8` · required
  >
  > Points at [`loan_counts`](#loan_counts).
- 🔢 order_created
  > `member_counts_2.order_created` · `int4` · required
- 🧩 device_amount
  > `member_counts_2.device_amount` · `jsonb` · required
- ☑️ order_kind_2
  > `member_counts_2.order_kind_2` · `bool` · required
- 🔗 seriess_id <!-- orbit link: "#seriess", status: "foreign key" -->
  > `member_counts_2.seriess_id` · `int8` · required
  >
  > Points at [`seriess`](#seriess).
- 🔢 fee_count
  > `member_counts_2.fee_count` · `int4` · required
- 🧩 book_name
  > `member_counts_2.book_name` · `jsonb` · required
- 🕒 budget_format
  > `member_counts_2.budget_format` · `timestamp` · required
- 🔤 audit_title
  > `member_counts_2.audit_title` · `varchar` · optional

### review_versions
<!-- orbit
id: "review_versions"
icon: "📗"
collapsed: true
-->
> `review_versions` · table in Fees & Payments
>
> One row per review, with 8 columns and about 753 thousand rows.
>
> **Belongs to** [`audit_links_4`](#audit_links_4)

- 🔑 id <!-- orbit status: "primary key" -->
  > `review_versions.id` · `int8` · required
- ☑️ copy_started
  > `review_versions.copy_started` · `bool` · required
- 🧩 event_name
  > `review_versions.event_name` · `jsonb` · required
- 🔗 license_id <!-- orbit link: "#license_logs_2", status: "foreign key" -->
  > `review_versions.license_id` · `int8` · required
  >
  > Points at [`license_logs_2`](#license_logs_2).
- 🧩 order_priority
  > `review_versions.order_priority` · `jsonb` · optional
- 🧩 event_language
  > `review_versions.event_language` · `jsonb` · required
- 🔗 report_id <!-- orbit link: "#report_links", status: "foreign key" -->
  > `review_versions.report_id` · `int8` · required
  >
  > Points at [`report_links`](#report_links).
- 🔢 audit_language
  > `review_versions.audit_language` · `int4` · required

### author_links_3
<!-- orbit
id: "author_links_3"
icon: "📇"
collapsed: true
-->
> `author_links_3` · table in Fees & Payments
>
> One row per author, with 12 columns and about 688 thousand rows.
>
> **Belongs to** [`room_logs`](#room_logs) · [`report_logs_2`](#report_logs_2)

- 🔑 id <!-- orbit status: "primary key" -->
  > `author_links_3.id` · `int8` · required
- 🕒 budget_started
  > `author_links_3.budget_started` · `timestamp` · optional
- 🔤 license_count
  > `author_links_3.license_count` · `varchar` · required
- 🔗 visit_id <!-- orbit link: "#visit_items", status: "foreign key" -->
  > `author_links_3.visit_id` · `int8` · required
  >
  > Points at [`visit_items`](#visit_items).
- 🧩 branch_total
  > `author_links_3.branch_total` · `jsonb` · required
- ☑️ vendor_archived
  > `author_links_3.vendor_archived` · `bool` · optional
- 🧩 branch_region
  > `author_links_3.branch_region` · `jsonb` · optional
- 🔤 series_amount
  > `author_links_3.series_amount` · `varchar` · required
- 🧩 series_created
  > `author_links_3.series_created` · `jsonb` · optional
- 🔗 donation_id <!-- orbit link: "#donation_history", status: "foreign key" -->
  > `author_links_3.donation_id` · `int8` · required
  >
  > Points at [`donation_history`](#donation_history).
- ☑️ shelf_region
  > `author_links_3.shelf_region` · `bool` · required
- 🕒 fee_label
  > `author_links_3.fee_label` · `timestamp` · required

### device_settings
<!-- orbit
id: "device_settings"
icon: "📗"
collapsed: true
-->
> `device_settings` · table in Fees & Payments
>
> One row per device, with 19 columns and about 218 thousand rows.
>
> **Belongs to** [`ebook_queues`](#ebook_queues) · [`license_settings`](#license_settings) · [`author_links_3`](#author_links_3)

- 🔑 id <!-- orbit status: "primary key" -->
  > `device_settings.id` · `int8` · required
- 🔤 repair_notes
  > `device_settings.repair_notes` · `varchar` · required
- 🔢 tag_started
  > `device_settings.tag_started` · `int4` · optional
- ☑️ role_external
  > `device_settings.role_external` · `bool` · optional
- 🧩 branch_reviewed
  > `device_settings.branch_reviewed` · `jsonb` · optional
- 🔢 loan_state
  > `device_settings.loan_state` · `int4` · required
- 🔢 tag_updated
  > `device_settings.tag_updated` · `int4` · required
- 🔤 branch_source
  > `device_settings.branch_source` · `varchar` · optional
- 🔢 visit_kind
  > `device_settings.visit_kind` · `int4` · optional
- 🔤 shelf_started
  > `device_settings.shelf_started` · `varchar` · optional
- 🔤 payment_reviewed
  > `device_settings.payment_reviewed` · `varchar` · optional
- 🔢 device_external
  > `device_settings.device_external` · `int4` · required
- 🧩 event_priority
  > `device_settings.event_priority` · `jsonb` · required
- 🧩 role_position
  > `device_settings.role_position` · `jsonb` · required
- 🔤 book_created
  > `device_settings.book_created` · `varchar` · optional
- 🔗 payment_id <!-- orbit link: "#payment_requests", status: "foreign key" -->
  > `device_settings.payment_id` · `int8` · required
  >
  > Points at [`payment_requests`](#payment_requests).
- 🧩 branch_count
  > `device_settings.branch_count` · `jsonb` · required
- 🔢 author_state
  > `device_settings.author_state` · `int4` · required
- 🔗 book_id <!-- orbit link: "#book_items", status: "foreign key" -->
  > `device_settings.book_id` · `int8` · required
  >
  > Points at [`book_items`](#book_items).

### staff_logs
<!-- orbit
id: "staff_logs"
icon: "🧮"
collapsed: true
-->
> `staff_logs` · table in Fees & Payments
>
> One row per staff, with 21 columns and about 318 thousand rows.
>
> **Belongs to** [`device_items`](#device_items) · [`author_history`](#author_history) · [`report_logs`](#report_logs) · [`member_counts`](#member_counts)

- 🔑 id <!-- orbit status: "primary key" -->
  > `staff_logs.id` · `int8` · required
- ☑️ shelf_reviewed
  > `staff_logs.shelf_reviewed` · `bool` · required
- 🔤 member_count
  > `staff_logs.member_count` · `varchar` · optional
- 🔗 staff_id <!-- orbit link: "#staff_counts", status: "foreign key" -->
  > `staff_logs.staff_id` · `int8` · required
  >
  > Points at [`staff_counts`](#staff_counts).
- 🧩 device_language
  > `staff_logs.device_language` · `jsonb` · required
- 🕒 ebook_amount
  > `staff_logs.ebook_amount` · `timestamp` · required
- 🔢 license_name
  > `staff_logs.license_name` · `int4` · required
- 🔗 vendor_id <!-- orbit link: "#vendor_counts", status: "foreign key" -->
  > `staff_logs.vendor_id` · `int8` · required
  >
  > Points at [`vendor_counts`](#vendor_counts).
- 🔤 role_public
  > `staff_logs.role_public` · `varchar` · required
- 🔗 loan_id <!-- orbit link: "#loan_logs_2", status: "foreign key" -->
  > `staff_logs.loan_id` · `int8` · required
  >
  > Points at [`loan_logs_2`](#loan_logs_2).
- ☑️ branch_kind
  > `staff_logs.branch_kind` · `bool` · required
- 🔢 license_format
  > `staff_logs.license_format` · `int4` · optional
- 🔢 budget_title
  > `staff_logs.budget_title` · `int4` · optional
- 🔗 event_id <!-- orbit link: "#event_queues", status: "foreign key" -->
  > `staff_logs.event_id` · `int8` · required
  >
  > Points at [`event_queues`](#event_queues).
- ☑️ loan_started
  > `staff_logs.loan_started` · `bool` · required
- ☑️ donation_active
  > `staff_logs.donation_active` · `bool` · optional
- ☑️ notice_label
  > `staff_logs.notice_label` · `bool` · optional
- 🔗 series_id <!-- orbit link: "#series_counts_2", status: "foreign key" -->
  > `staff_logs.series_id` · `int8` · required
  >
  > Points at [`series_counts_2`](#series_counts_2).
- 🕒 vendor_started
  > `staff_logs.vendor_started` · `timestamp` · optional
- 🔗 tag_id <!-- orbit link: "#tag_settings", status: "foreign key" -->
  > `staff_logs.tag_id` · `int8` · required
  >
  > Points at [`tag_settings`](#tag_settings).
- 🔤 vendor_active
  > `staff_logs.vendor_active` · `varchar` · required

### loan_counts
<!-- orbit
id: "loan_counts"
icon: "📗"
collapsed: true
-->
> `loan_counts` · table in Fees & Payments
>
> One row per loan, with 13 columns and about 672 thousand rows.
>
> **Belongs to** [`role_counts`](#role_counts) · [`license_queues`](#license_queues) · [`device_settings`](#device_settings)

- 🔑 id <!-- orbit status: "primary key" -->
  > `loan_counts.id` · `int8` · required
- 🔗 notice_id <!-- orbit link: "#notice_counts", status: "foreign key" -->
  > `loan_counts.notice_id` · `int8` · required
  >
  > Points at [`notice_counts`](#notice_counts).
- 🧩 series_code
  > `loan_counts.series_code` · `jsonb` · optional
- ☑️ role_ended
  > `loan_counts.role_ended` · `bool` · required
- 🔤 shelf_updated
  > `loan_counts.shelf_updated` · `varchar` · required
- 🔤 branch_public
  > `loan_counts.branch_public` · `varchar` · required
- 🔢 fee_priority
  > `loan_counts.fee_priority` · `int4` · optional
- 🕒 event_external
  > `loan_counts.event_external` · `timestamp` · optional
- ☑️ room_code
  > `loan_counts.room_code` · `bool` · required
- 🔤 license_position
  > `loan_counts.license_position` · `varchar` · optional
- 🕒 staff_label
  > `loan_counts.staff_label` · `timestamp` · required
- ☑️ vendor_kind
  > `loan_counts.vendor_kind` · `bool` · optional
- ☑️ author_position
  > `loan_counts.author_position` · `bool` · optional

### report_queues_2
<!-- orbit
id: "report_queues_2"
icon: "🧷"
collapsed: true
-->
> `report_queues_2` · table in Fees & Payments
>
> One row per report, with 24 columns and about 666 thousand rows.
>
> **Belongs to** [`vendor_counts`](#vendor_counts) · [`copy_versions`](#copy_versions) · [`ebook_logs`](#ebook_logs)

- 🔑 id <!-- orbit status: "primary key" -->
  > `report_queues_2.id` · `int8` · required
- ☑️ repair_archived
  > `report_queues_2.repair_archived` · `bool` · optional
- 🧩 donation_amount
  > `report_queues_2.donation_amount` · `jsonb` · optional
- 🕒 hold_source
  > `report_queues_2.hold_source` · `timestamp` · required
- 🔗 tags_id <!-- orbit link: "#tags", status: "foreign key" -->
  > `report_queues_2.tags_id` · `int8` · required
  >
  > Points at [`tags`](#tags).
- 🔗 vendor_id <!-- orbit link: "#vendor_history", status: "foreign key" -->
  > `report_queues_2.vendor_id` · `int8` · required
  >
  > Points at [`vendor_history`](#vendor_history).
- 🔤 repair_state
  > `report_queues_2.repair_state` · `varchar` · optional
- 🧩 payment_ended
  > `report_queues_2.payment_ended` · `jsonb` · required
- 🕒 repair_kind
  > `report_queues_2.repair_kind` · `timestamp` · required
- 🕒 member_status
  > `report_queues_2.member_status` · `timestamp` · required
- 🔢 repair_name
  > `report_queues_2.repair_name` · `int4` · optional
- 🔗 series_id <!-- orbit link: "#series_requests", status: "foreign key" -->
  > `report_queues_2.series_id` · `int8` · required
  >
  > Points at [`series_requests`](#series_requests).
- 🔗 audit_id <!-- orbit link: "#audit_links_4", status: "foreign key" -->
  > `report_queues_2.audit_id` · `int8` · required
  >
  > Points at [`audit_links_4`](#audit_links_4).
- 🔢 order_language
  > `report_queues_2.order_language` · `int4` · optional
- ☑️ shelf_kind
  > `report_queues_2.shelf_kind` · `bool` · optional
- ☑️ ebook_language
  > `report_queues_2.ebook_language` · `bool` · optional
- 🔗 budget_id <!-- orbit link: "#budget_queues", status: "foreign key" -->
  > `report_queues_2.budget_id` · `int8` · required
  >
  > Points at [`budget_queues`](#budget_queues).
- 🕒 order_active
  > `report_queues_2.order_active` · `timestamp` · optional
- ☑️ shelf_total
  > `report_queues_2.shelf_total` · `bool` · required
- 🧩 device_state
  > `report_queues_2.device_state` · `jsonb` · required
- 🔤 budget_public
  > `report_queues_2.budget_public` · `varchar` · required
- 🔗 copy_id <!-- orbit link: "#copy_links", status: "foreign key" -->
  > `report_queues_2.copy_id` · `int8` · required
  >
  > Points at [`copy_links`](#copy_links).
- 🔗 hold_id <!-- orbit link: "#hold_links_2", status: "foreign key" -->
  > `report_queues_2.hold_id` · `int8` · required
  >
  > Points at [`hold_links_2`](#hold_links_2).
- ☑️ budget_created
  > `report_queues_2.budget_created` · `bool` · required

### card_logs
<!-- orbit
id: "card_logs"
icon: "🗃️"
collapsed: true
-->
> `card_logs` · table in Fees & Payments
>
> One row per card, with 15 columns and about 177 thousand rows.
>
> **Belongs to** [`vendor_history`](#vendor_history) · [`budgets`](#budgets) · [`fee_requests`](#fee_requests) · [`shelf_queues`](#shelf_queues)

- 🔑 id <!-- orbit status: "primary key" -->
  > `card_logs.id` · `int8` · required
- 🧩 room_priority
  > `card_logs.room_priority` · `jsonb` · optional
- 🔗 review_id <!-- orbit link: "#review_logs", status: "foreign key" -->
  > `card_logs.review_id` · `int8` · required
  >
  > Points at [`review_logs`](#review_logs).
- ☑️ report_total
  > `card_logs.report_total` · `bool` · required
- 🔗 audit_id <!-- orbit link: "#audit_settings", status: "foreign key" -->
  > `card_logs.audit_id` · `int8` · required
  >
  > Points at [`audit_settings`](#audit_settings).
- 🕒 room_status
  > `card_logs.room_status` · `timestamp` · optional
- 🧩 repair_updated
  > `card_logs.repair_updated` · `jsonb` · required
- ☑️ repair_count
  > `card_logs.repair_count` · `bool` · required
- 🧩 review_source
  > `card_logs.review_source` · `jsonb` · required
- 🔤 ebook_active
  > `card_logs.ebook_active` · `varchar` · optional
- ☑️ tag_ended
  > `card_logs.tag_ended` · `bool` · required
- ☑️ notice_public
  > `card_logs.notice_public` · `bool` · required
- 🔗 ebook_id <!-- orbit link: "#ebook_versions", status: "foreign key" -->
  > `card_logs.ebook_id` · `int8` · required
  >
  > Points at [`ebook_versions`](#ebook_versions).
- ☑️ fee_amount
  > `card_logs.fee_amount` · `bool` · optional
- 🔤 copy_region
  > `card_logs.copy_region` · `varchar` · required

### donation_queues
<!-- orbit
id: "donation_queues"
icon: "🧮"
collapsed: true
-->
> `donation_queues` · table in Fees & Payments
>
> One row per donation, with 15 columns and about 114 thousand rows.
>
> **Belongs to** [`role_counts`](#role_counts) · [`author_history`](#author_history)

- 🔑 id <!-- orbit status: "primary key" -->
  > `donation_queues.id` · `int8` · required
- 🔢 budget_count
  > `donation_queues.budget_count` · `int4` · optional
- 🕒 license_notes
  > `donation_queues.license_notes` · `timestamp` · optional
- 🔢 staff_public
  > `donation_queues.staff_public` · `int4` · optional
- 🔤 book_kind
  > `donation_queues.book_kind` · `varchar` · optional
- ☑️ room_count
  > `donation_queues.room_count` · `bool` · required
- 🔢 branch_active
  > `donation_queues.branch_active` · `int4` · optional
- 🧩 visit_ended
  > `donation_queues.visit_ended` · `jsonb` · required
- 🕒 loan_state
  > `donation_queues.loan_state` · `timestamp` · optional
- 🔗 holds_id <!-- orbit link: "#holds", status: "foreign key" -->
  > `donation_queues.holds_id` · `int8` · required
  >
  > Points at [`holds`](#holds).
- 🔢 donation_source
  > `donation_queues.donation_source` · `int4` · required
- ☑️ donation_label
  > `donation_queues.donation_label` · `bool` · optional
- 🔢 ebook_priority
  > `donation_queues.ebook_priority` · `int4` · optional
- ☑️ donation_title
  > `donation_queues.donation_title` · `bool` · required
- 🕒 report_amount
  > `donation_queues.report_amount` · `timestamp` · optional

### author_history
<!-- orbit
id: "author_history"
icon: "🧷"
collapsed: true
-->
> `author_history` · table in Fees & Payments
>
> One row per author, with 23 columns and about 380 thousand rows.
>
> **Belongs to** [`vendor_history`](#vendor_history)

- 🔑 id <!-- orbit status: "primary key" -->
  > `author_history.id` · `int8` · required
- ☑️ notice_language
  > `author_history.notice_language` · `bool` · required
- 🕒 budget_source
  > `author_history.budget_source` · `timestamp` · required
- 🕒 event_ended
  > `author_history.event_ended` · `timestamp` · required
- 🔗 events_id <!-- orbit link: "#events", status: "foreign key" -->
  > `author_history.events_id` · `int8` · required
  >
  > Points at [`events`](#events).
- 🕒 room_position
  > `author_history.room_position` · `timestamp` · required
- ☑️ order_total
  > `author_history.order_total` · `bool` · required
- 🔗 budget_id <!-- orbit link: "#budget_settings_2", status: "foreign key" -->
  > `author_history.budget_id` · `int8` · required
  >
  > Points at [`budget_settings_2`](#budget_settings_2).
- 🧩 staff_external
  > `author_history.staff_external` · `jsonb` · required
- 🔗 author_id <!-- orbit link: "#author_links_2", status: "foreign key" -->
  > `author_history.author_id` · `int8` · required
  >
  > Points at [`author_links_2`](#author_links_2).
- 🔗 book_id <!-- orbit link: "#book_items", status: "foreign key" -->
  > `author_history.book_id` · `int8` · required
  >
  > Points at [`book_items`](#book_items).
- 🕒 payment_source
  > `author_history.payment_source` · `timestamp` · optional
- 🧩 order_name
  > `author_history.order_name` · `jsonb` · required
- 🔢 ebook_active
  > `author_history.ebook_active` · `int4` · required
- 🧩 card_source
  > `author_history.card_source` · `jsonb` · optional
- 🔤 vendor_notes
  > `author_history.vendor_notes` · `varchar` · required
- 🕒 loan_reviewed
  > `author_history.loan_reviewed` · `timestamp` · optional
- 🔗 branch_id <!-- orbit link: "#branch_links", status: "foreign key" -->
  > `author_history.branch_id` · `int8` · required
  >
  > Points at [`branch_links`](#branch_links).
- 🔗 tag_id <!-- orbit link: "#tag_settings", status: "foreign key" -->
  > `author_history.tag_id` · `int8` · required
  >
  > Points at [`tag_settings`](#tag_settings).
- 🕒 visit_reviewed
  > `author_history.visit_reviewed` · `timestamp` · optional
- 🧩 payment_format
  > `author_history.payment_format` · `jsonb` · optional
- 🧩 hold_position
  > `author_history.hold_position` · `jsonb` · required
- 🔗 review_id <!-- orbit link: "#review_queues", status: "foreign key" -->
  > `author_history.review_id` · `int8` · required
  >
  > Points at [`review_queues`](#review_queues).

### repair_requests
<!-- orbit
id: "repair_requests"
icon: "📇"
collapsed: true
-->
> `repair_requests` · table in Fees & Payments
>
> One row per repair, with 23 columns and about 93 thousand rows.
>
> **Belongs to** [`copy_links`](#copy_links) · [`notice_counts`](#notice_counts) · [`license_versions`](#license_versions) · [`role_counts`](#role_counts)

- 🔑 id <!-- orbit status: "primary key" -->
  > `repair_requests.id` · `int8` · required
- ☑️ donation_label
  > `repair_requests.donation_label` · `bool` · optional
- 🔗 role_id <!-- orbit link: "#role_counts", status: "foreign key" -->
  > `repair_requests.role_id` · `int8` · required
  >
  > Points at [`role_counts`](#role_counts).
- 🕒 book_archived
  > `repair_requests.book_archived` · `timestamp` · optional
- 🔤 tag_source
  > `repair_requests.tag_source` · `varchar` · required
- 🧩 series_priority
  > `repair_requests.series_priority` · `jsonb` · required
- 🕒 report_position
  > `repair_requests.report_position` · `timestamp` · optional
- 🔢 audit_reviewed
  > `repair_requests.audit_reviewed` · `int4` · required
- ☑️ role_reviewed
  > `repair_requests.role_reviewed` · `bool` · required
- ☑️ review_amount
  > `repair_requests.review_amount` · `bool` · optional
- 🕒 staff_position
  > `repair_requests.staff_position` · `timestamp` · required
- 🔗 repair_id <!-- orbit link: "#repair_requests", status: "foreign key" -->
  > `repair_requests.repair_id` · `int8` · required
  >
  > Points at [`repair_requests`](#repair_requests).
- 🧩 book_code
  > `repair_requests.book_code` · `jsonb` · optional
- 🕒 donation_reviewed
  > `repair_requests.donation_reviewed` · `timestamp` · optional
- ☑️ visit_label
  > `repair_requests.visit_label` · `bool` · required
- 🔢 donation_created
  > `repair_requests.donation_created` · `int4` · required
- 🔤 device_language
  > `repair_requests.device_language` · `varchar` · required
- 🕒 shelf_amount
  > `repair_requests.shelf_amount` · `timestamp` · required
- 🔗 shelf_id <!-- orbit link: "#shelf_counts", status: "foreign key" -->
  > `repair_requests.shelf_id` · `int8` · required
  >
  > Points at [`shelf_counts`](#shelf_counts).
- 🔢 fee_ended
  > `repair_requests.fee_ended` · `int4` · optional
- 🕒 book_code_2
  > `repair_requests.book_code_2` · `timestamp` · optional
- 🧩 event_updated
  > `repair_requests.event_updated` · `jsonb` · optional
- 🕒 card_amount
  > `repair_requests.card_amount` · `timestamp` · optional

### tags
<!-- orbit
id: "tags"
icon: "🧮"
collapsed: true
-->
> `tags` · table in Fees & Payments
>
> One row per tags, with 7 columns and about 114 thousand rows.
>
> **Belongs to** [`hold_links_2`](#hold_links_2)

- 🔑 id <!-- orbit status: "primary key" -->
  > `tags.id` · `int8` · required
- ☑️ fee_created
  > `tags.fee_created` · `bool` · required
- 🕒 loan_archived
  > `tags.loan_archived` · `timestamp` · optional
- ☑️ review_language
  > `tags.review_language` · `bool` · required
- 🔢 tag_status
  > `tags.tag_status` · `int4` · optional
- 🔤 budget_amount
  > `tags.budget_amount` · `varchar` · optional
- ☑️ copy_total
  > `tags.copy_total` · `bool` · required

## 📦 Acquisitions
<!-- orbit
id: "area-acquisitions"
color: "#0EA5E9"
-->
> 9 tables. Largest: [`member_versions`](#member_versions), [`shelf_counts`](#shelf_counts), [`payment_versions`](#payment_versions).
>
> **Linked to** [`donation_requests`](#donation_requests) · [`ebook_versions`](#ebook_versions) · [`copy_history`](#copy_history)

### member_versions
<!-- orbit
id: "member_versions"
icon: "📇"
collapsed: true
-->
> `member_versions` · table in Acquisitions
>
> One row per member, with 9 columns and about 355 thousand rows.
>
> **Belongs to** [`fee_requests`](#fee_requests) · [`series_counts`](#series_counts)

- 🔑 id <!-- orbit status: "primary key" -->
  > `member_versions.id` · `int8` · required
- 🔢 vendor_name
  > `member_versions.vendor_name` · `int4` · required
- 🔢 visit_notes
  > `member_versions.visit_notes` · `int4` · required
- ☑️ event_position
  > `member_versions.event_position` · `bool` · required
- 🔗 member_id <!-- orbit link: "#member_counts_2", status: "foreign key" -->
  > `member_versions.member_id` · `int8` · required
  >
  > Points at [`member_counts_2`](#member_counts_2).
- ☑️ audit_active
  > `member_versions.audit_active` · `bool` · optional
- 🔤 payment_region
  > `member_versions.payment_region` · `varchar` · optional
- 🕒 branch_created
  > `member_versions.branch_created` · `timestamp` · optional
- ☑️ shelf_reviewed
  > `member_versions.shelf_reviewed` · `bool` · optional

### shelf_counts
<!-- orbit
id: "shelf_counts"
icon: "📇"
collapsed: true
-->
> `shelf_counts` · table in Acquisitions
>
> One row per shelf, with 19 columns and about 124 thousand rows.
>
> **Belongs to** [`budget_settings_2`](#budget_settings_2) · [`license_queues_2`](#license_queues_2)

- 🔑 id <!-- orbit status: "primary key" -->
  > `shelf_counts.id` · `int8` · required
- 🔤 device_position
  > `shelf_counts.device_position` · `varchar` · optional
- ☑️ fee_status
  > `shelf_counts.fee_status` · `bool` · optional
- 🔢 role_label
  > `shelf_counts.role_label` · `int4` · required
- 🔢 staff_total
  > `shelf_counts.staff_total` · `int4` · optional
- 🧩 loan_source
  > `shelf_counts.loan_source` · `jsonb` · required
- 🔢 series_region
  > `shelf_counts.series_region` · `int4` · required
- 🔢 fee_format
  > `shelf_counts.fee_format` · `int4` · required
- 🧩 ebook_source
  > `shelf_counts.ebook_source` · `jsonb` · optional
- 🕒 room_ended
  > `shelf_counts.room_ended` · `timestamp` · optional
- ☑️ room_reviewed
  > `shelf_counts.room_reviewed` · `bool` · optional
- ☑️ shelf_position
  > `shelf_counts.shelf_position` · `bool` · required
- 🔤 event_total
  > `shelf_counts.event_total` · `varchar` · required
- 🕒 branch_name
  > `shelf_counts.branch_name` · `timestamp` · required
- 🔗 report_id <!-- orbit link: "#report_links", status: "foreign key" -->
  > `shelf_counts.report_id` · `int8` · required
  >
  > Points at [`report_links`](#report_links).
- 🕒 notice_started
  > `shelf_counts.notice_started` · `timestamp` · required
- 🕒 license_amount
  > `shelf_counts.license_amount` · `timestamp` · optional
- 🔗 license_id <!-- orbit link: "#license_queues_2", status: "foreign key" -->
  > `shelf_counts.license_id` · `int8` · required
  >
  > Points at [`license_queues_2`](#license_queues_2).
- 🔢 loan_language
  > `shelf_counts.loan_language` · `int4` · required

### payment_versions
<!-- orbit
id: "payment_versions"
icon: "🧷"
collapsed: true
-->
> `payment_versions` · table in Acquisitions
>
> One row per payment, with 5 columns and about 504 thousand rows.
>
> **Belongs to** [`hold_logs`](#hold_logs) · [`room_counts`](#room_counts)

- 🔑 id <!-- orbit status: "primary key" -->
  > `payment_versions.id` · `int8` · required
- 🔗 copy_id <!-- orbit link: "#copy_history", status: "foreign key" -->
  > `payment_versions.copy_id` · `int8` · required
  >
  > Points at [`copy_history`](#copy_history).
- 🧩 report_amount
  > `payment_versions.report_amount` · `jsonb` · optional
- 🧩 card_label
  > `payment_versions.card_label` · `jsonb` · required
- 🧩 author_started
  > `payment_versions.author_started` · `jsonb` · optional

### room_counts
<!-- orbit
id: "room_counts"
icon: "🧮"
collapsed: true
-->
> `room_counts` · table in Acquisitions
>
> One row per room, with 18 columns and about 653 thousand rows.
>
> **Belongs to** [`audit_links_4`](#audit_links_4) · [`payment_versions`](#payment_versions) · [`license_logs`](#license_logs) · [`room_logs`](#room_logs)

- 🔑 id <!-- orbit status: "primary key" -->
  > `room_counts.id` · `int8` · required
- 🔢 license_label
  > `room_counts.license_label` · `int4` · optional
- 🧩 payment_total
  > `room_counts.payment_total` · `jsonb` · required
- 🔤 vendor_public
  > `room_counts.vendor_public` · `varchar` · required
- ☑️ tag_archived
  > `room_counts.tag_archived` · `bool` · optional
- 🧩 card_source
  > `room_counts.card_source` · `jsonb` · optional
- 🔢 hold_priority
  > `room_counts.hold_priority` · `int4` · optional
- ☑️ report_name
  > `room_counts.report_name` · `bool` · optional
- 🔢 license_notes
  > `room_counts.license_notes` · `int4` · required
- ☑️ visit_state
  > `room_counts.visit_state` · `bool` · optional
- 🔗 audit_id <!-- orbit link: "#audit_links_3", status: "foreign key" -->
  > `room_counts.audit_id` · `int8` · required
  >
  > Points at [`audit_links_3`](#audit_links_3).
- 🔗 events_id <!-- orbit link: "#events", status: "foreign key" -->
  > `room_counts.events_id` · `int8` · required
  >
  > Points at [`events`](#events).
- 🧩 staff_total
  > `room_counts.staff_total` · `jsonb` · optional
- ☑️ payment_status
  > `room_counts.payment_status` · `bool` · optional
- 🧩 author_label
  > `room_counts.author_label` · `jsonb` · optional
- 🔗 book_id <!-- orbit link: "#book_links", status: "foreign key" -->
  > `room_counts.book_id` · `int8` · required
  >
  > Points at [`book_links`](#book_links).
- 🔗 repair_id <!-- orbit link: "#repair_requests", status: "foreign key" -->
  > `room_counts.repair_id` · `int8` · required
  >
  > Points at [`repair_requests`](#repair_requests).
- ☑️ hold_state
  > `room_counts.hold_state` · `bool` · optional

### order_items
<!-- orbit
id: "order_items"
icon: "🧮"
collapsed: true
-->
> `order_items` · table in Acquisitions
>
> One row per order, with 14 columns and about 879 thousand rows.
>
> **Belongs to** [`license_logs_2`](#license_logs_2) · [`budgets`](#budgets) · [`report_queues`](#report_queues) · [`tag_requests`](#tag_requests)

- 🔑 id <!-- orbit status: "primary key" -->
  > `order_items.id` · `int8` · required
- 🔗 rooms_id <!-- orbit link: "#rooms", status: "foreign key" -->
  > `order_items.rooms_id` · `int8` · required
  >
  > Points at [`rooms`](#rooms).
- 🔢 donation_title
  > `order_items.donation_title` · `int4` · required
- ☑️ review_language
  > `order_items.review_language` · `bool` · required
- ☑️ visit_region
  > `order_items.visit_region` · `bool` · optional
- 🧩 copy_updated
  > `order_items.copy_updated` · `jsonb` · required
- 🔤 budget_archived
  > `order_items.budget_archived` · `varchar` · optional
- 🔢 license_code
  > `order_items.license_code` · `int4` · required
- 🔤 role_title
  > `order_items.role_title` · `varchar` · optional
- 🕒 order_archived
  > `order_items.order_archived` · `timestamp` · optional
- 🔤 branch_name
  > `order_items.branch_name` · `varchar` · optional
- 🧩 license_status
  > `order_items.license_status` · `jsonb` · optional
- 🧩 payment_total
  > `order_items.payment_total` · `jsonb` · optional
- 🔢 report_code
  > `order_items.report_code` · `int4` · required

### report_items
<!-- orbit
id: "report_items"
icon: "🧮"
collapsed: true
-->
> `report_items` · table in Acquisitions
>
> One row per report, with 20 columns and about 41 thousand rows.
>
> **Belongs to** [`device_items`](#device_items) · [`fee_requests`](#fee_requests) · [`visit_queues`](#visit_queues)

- 🔑 id <!-- orbit status: "primary key" -->
  > `report_items.id` · `int8` · required
- 🧩 report_created
  > `report_items.report_created` · `jsonb` · required
- 🧩 license_source
  > `report_items.license_source` · `jsonb` · optional
- 🔢 repair_source
  > `report_items.repair_source` · `int4` · optional
- 🧩 event_ended
  > `report_items.event_ended` · `jsonb` · required
- 🔢 loan_title
  > `report_items.loan_title` · `int4` · optional
- 🔗 donation_id <!-- orbit link: "#donation_requests", status: "foreign key" -->
  > `report_items.donation_id` · `int8` · required
  >
  > Points at [`donation_requests`](#donation_requests).
- 🔤 ebook_amount
  > `report_items.ebook_amount` · `varchar` · optional
- 🔢 shelf_archived
  > `report_items.shelf_archived` · `int4` · optional
- 🧩 order_created
  > `report_items.order_created` · `jsonb` · required
- 🧩 device_code
  > `report_items.device_code` · `jsonb` · optional
- 🧩 visit_state
  > `report_items.visit_state` · `jsonb` · required
- 🕒 branch_code
  > `report_items.branch_code` · `timestamp` · optional
- 🧩 copy_public
  > `report_items.copy_public` · `jsonb` · optional
- 🔢 shelf_public
  > `report_items.shelf_public` · `int4` · optional
- 🧩 ebook_label
  > `report_items.ebook_label` · `jsonb` · optional
- 🧩 author_ended
  > `report_items.author_ended` · `jsonb` · required
- 🧩 ebook_name
  > `report_items.ebook_name` · `jsonb` · required
- 🔤 budget_public
  > `report_items.budget_public` · `varchar` · required
- 🧩 loan_kind
  > `report_items.loan_kind` · `jsonb` · required

### ebook_queues
<!-- orbit
id: "ebook_queues"
icon: "📇"
collapsed: true
-->
> `ebook_queues` · table in Acquisitions
>
> One row per ebook, with 4 columns and about 346 thousand rows.
>
> **Belongs to** [`book_links`](#book_links) · [`license_logs`](#license_logs) · [`card_links_2`](#card_links_2) · [`donation_links`](#donation_links)

- 🔑 id <!-- orbit status: "primary key" -->
  > `ebook_queues.id` · `int8` · required
- 🕒 license_position
  > `ebook_queues.license_position` · `timestamp` · required
- 🕒 event_count
  > `ebook_queues.event_count` · `timestamp` · required
- 🕒 fee_format
  > `ebook_queues.fee_format` · `timestamp` · required

### license_queues
<!-- orbit
id: "license_queues"
icon: "🧷"
collapsed: true
-->
> `license_queues` · table in Acquisitions
>
> One row per license, with 9 columns and about 480 thousand rows.
>
> **Belongs to** [`review_queues`](#review_queues) · [`ebook_versions`](#ebook_versions) · [`fee_requests`](#fee_requests) · [`series_requests`](#series_requests)

- 🔑 id <!-- orbit status: "primary key" -->
  > `license_queues.id` · `int8` · required
- 🕒 payment_priority
  > `license_queues.payment_priority` · `timestamp` · required
- ☑️ donation_reviewed
  > `license_queues.donation_reviewed` · `bool` · required
- ☑️ fee_total
  > `license_queues.fee_total` · `bool` · required
- 🧩 book_amount
  > `license_queues.book_amount` · `jsonb` · required
- 🔗 tag_id <!-- orbit link: "#tag_requests", status: "foreign key" -->
  > `license_queues.tag_id` · `int8` · required
  >
  > Points at [`tag_requests`](#tag_requests).
- 🔢 series_notes
  > `license_queues.series_notes` · `int4` · required
- ☑️ budget_code
  > `license_queues.budget_code` · `bool` · required
- 🔤 ebook_started
  > `license_queues.ebook_started` · `varchar` · required

### notice_requests
<!-- orbit
id: "notice_requests"
icon: "🧮"
collapsed: true
-->
> `notice_requests` · table in Acquisitions
>
> One row per notice, with 5 columns and about 269 thousand rows.
>
> **Belongs to** [`report_logs`](#report_logs)

- 🔑 id <!-- orbit status: "primary key" -->
  > `notice_requests.id` · `int8` · required
- 🧩 series_created
  > `notice_requests.series_created` · `jsonb` · optional
- 🕒 vendor_external
  > `notice_requests.vendor_external` · `timestamp` · required
- 🔗 role_id <!-- orbit link: "#role_counts", status: "foreign key" -->
  > `notice_requests.role_id` · `int8` · required
  >
  > Points at [`role_counts`](#role_counts).
- 🔢 budget_state
  > `notice_requests.budget_state` · `int4` · optional

## 🧾 Reports
<!-- orbit
id: "area-reports"
color: "#64748B"
-->
> 12 tables. Largest: [`ebook_logs`](#ebook_logs), [`vendor_history`](#vendor_history), [`device_items`](#device_items).
>
> **Linked to** [`order_logs`](#order_logs) · [`report_logs_2`](#report_logs_2) · [`audit_settings`](#audit_settings)

### ebook_logs
<!-- orbit
id: "ebook_logs"
icon: "🧮"
collapsed: true
-->
> `ebook_logs` · table in Reports
>
> One row per ebook, with 70 columns and about 735 thousand rows.
>
> **Belongs to** [`audit_links_3`](#audit_links_3) · [`member_counts`](#member_counts) · [`audit_settings`](#audit_settings)

- 🔑 id <!-- orbit status: "primary key" -->
  > `ebook_logs.id` · `int8` · required
- 🧩 donation_updated
  > `ebook_logs.donation_updated` · `jsonb` · required
- 🔢 audit_format
  > `ebook_logs.audit_format` · `int4` · required
- 🔤 review_kind
  > `ebook_logs.review_kind` · `varchar` · required
- 🧩 donation_count
  > `ebook_logs.donation_count` · `jsonb` · required
- 🔢 shelf_total
  > `ebook_logs.shelf_total` · `int4` · optional
- 🔗 visit_id <!-- orbit link: "#visit_items", status: "foreign key" -->
  > `ebook_logs.visit_id` · `int8` · required
  >
  > Points at [`visit_items`](#visit_items).
- ☑️ role_ended
  > `ebook_logs.role_ended` · `bool` · optional
- 🔤 audit_archived
  > `ebook_logs.audit_archived` · `varchar` · optional
- 🔢 order_format
  > `ebook_logs.order_format` · `int4` · required
- 🧩 series_kind
  > `ebook_logs.series_kind` · `jsonb` · optional
- 🔤 copy_position
  > `ebook_logs.copy_position` · `varchar` · optional
- 🧩 author_code
  > `ebook_logs.author_code` · `jsonb` · required
- 🔗 budget_id <!-- orbit link: "#budget_settings", status: "foreign key" -->
  > `ebook_logs.budget_id` · `int8` · required
  >
  > Points at [`budget_settings`](#budget_settings).
- 🧩 series_name
  > `ebook_logs.series_name` · `jsonb` · optional
- ☑️ author_active
  > `ebook_logs.author_active` · `bool` · optional
- ☑️ hold_title
  > `ebook_logs.hold_title` · `bool` · required
- ☑️ notice_notes
  > `ebook_logs.notice_notes` · `bool` · optional
- 🔢 device_updated
  > `ebook_logs.device_updated` · `int4` · optional
- 🔢 report_kind
  > `ebook_logs.report_kind` · `int4` · required
- ☑️ hold_external
  > `ebook_logs.hold_external` · `bool` · optional
- 🕒 report_notes
  > `ebook_logs.report_notes` · `timestamp` · optional
- 🕒 room_reviewed
  > `ebook_logs.room_reviewed` · `timestamp` · required
- ☑️ report_updated
  > `ebook_logs.report_updated` · `bool` · optional
- 🔗 review_id <!-- orbit link: "#review_links", status: "foreign key" -->
  > `ebook_logs.review_id` · `int8` · required
  >
  > Points at [`review_links`](#review_links).
- 🔗 ebook_id <!-- orbit link: "#ebook_requests", status: "foreign key" -->
  > `ebook_logs.ebook_id` · `int8` · required
  >
  > Points at [`ebook_requests`](#ebook_requests).
- ☑️ author_external
  > `ebook_logs.author_external` · `bool` · required
- 🔤 book_label
  > `ebook_logs.book_label` · `varchar` · optional
- 🔗 member_id <!-- orbit link: "#member_versions", status: "foreign key" -->
  > `ebook_logs.member_id` · `int8` · required
  >
  > Points at [`member_versions`](#member_versions).
- 🔤 copy_status
  > `ebook_logs.copy_status` · `varchar` · optional
- ☑️ copy_amount
  > `ebook_logs.copy_amount` · `bool` · required
- ☑️ book_reviewed
  > `ebook_logs.book_reviewed` · `bool` · required
- 🕒 license_priority
  > `ebook_logs.license_priority` · `timestamp` · required
- ☑️ card_count
  > `ebook_logs.card_count` · `bool` · required
- 🧩 report_notes_2
  > `ebook_logs.report_notes_2` · `jsonb` · required
- 🔢 notice_language
  > `ebook_logs.notice_language` · `int4` · optional
- 🕒 branch_position
  > `ebook_logs.branch_position` · `timestamp` · optional
- 🔗 role_id <!-- orbit link: "#role_counts", status: "foreign key" -->
  > `ebook_logs.role_id` · `int8` · required
  >
  > Points at [`role_counts`](#role_counts).
- 🔤 license_language
  > `ebook_logs.license_language` · `varchar` · optional
- 🧩 review_ended
  > `ebook_logs.review_ended` · `jsonb` · required
- 🔗 device_id <!-- orbit link: "#device_items", status: "foreign key" -->
  > `ebook_logs.device_id` · `int8` · required
  >
  > Points at [`device_items`](#device_items).
- 🔗 card_id <!-- orbit link: "#card_queues", status: "foreign key" -->
  > `ebook_logs.card_id` · `int8` · required
  >
  > Points at [`card_queues`](#card_queues).
- 🔤 payment_notes
  > `ebook_logs.payment_notes` · `varchar` · optional
- 🔗 author_id <!-- orbit link: "#author_links_2", status: "foreign key" -->
  > `ebook_logs.author_id` · `int8` · required
  >
  > Points at [`author_links_2`](#author_links_2).
- 🕒 staff_notes
  > `ebook_logs.staff_notes` · `timestamp` · required
- 🔗 repair_id <!-- orbit link: "#repair_requests", status: "foreign key" -->
  > `ebook_logs.repair_id` · `int8` · required
  >
  > Points at [`repair_requests`](#repair_requests).
- 🔤 member_reviewed
  > `ebook_logs.member_reviewed` · `varchar` · required
- 🔗 book_id <!-- orbit link: "#book_settings", status: "foreign key" -->
  > `ebook_logs.book_id` · `int8` · required
  >
  > Points at [`book_settings`](#book_settings).
- 🔢 audit_count
  > `ebook_logs.audit_count` · `int4` · optional
- ☑️ card_active
  > `ebook_logs.card_active` · `bool` · required
- 🔗 budgets_id <!-- orbit link: "#budgets", status: "foreign key" -->
  > `ebook_logs.budgets_id` · `int8` · required
  >
  > Points at [`budgets`](#budgets).
- 🔤 role_kind
  > `ebook_logs.role_kind` · `varchar` · optional
- 🔤 report_name
  > `ebook_logs.report_name` · `varchar` · optional
- ☑️ fee_label
  > `ebook_logs.fee_label` · `bool` · required
- 🕒 role_position
  > `ebook_logs.role_position` · `timestamp` · required
- 🔗 author_id_2 <!-- orbit link: "#author_history", status: "foreign key" -->
  > `ebook_logs.author_id_2` · `int8` · required
  >
  > Points at [`author_history`](#author_history).
- 🔗 audit_id <!-- orbit link: "#audit_settings", status: "foreign key" -->
  > `ebook_logs.audit_id` · `int8` · required
  >
  > Points at [`audit_settings`](#audit_settings).
- 🔢 event_state
  > `ebook_logs.event_state` · `int4` · required
- 🕒 vendor_total
  > `ebook_logs.vendor_total` · `timestamp` · optional
- 🕒 vendor_format
  > `ebook_logs.vendor_format` · `timestamp` · required
- 🧩 hold_active
  > `ebook_logs.hold_active` · `jsonb` · required
- ☑️ budget_name
  > `ebook_logs.budget_name` · `bool` · optional
- 🔢 device_format
  > `ebook_logs.device_format` · `int4` · required
- 🧩 vendor_region
  > `ebook_logs.vendor_region` · `jsonb` · required
- 🔤 device_external
  > `ebook_logs.device_external` · `varchar` · required
- 🔗 card_id_2 <!-- orbit link: "#card_queues", status: "foreign key" -->
  > `ebook_logs.card_id_2` · `int8` · required
  >
  > Points at [`card_queues`](#card_queues).
- 🕒 visit_format
  > `ebook_logs.visit_format` · `timestamp` · required
- ☑️ room_updated
  > `ebook_logs.room_updated` · `bool` · required
- 🧩 room_title
  > `ebook_logs.room_title` · `jsonb` · required
- 🔗 order_id <!-- orbit link: "#order_links", status: "foreign key" -->
  > `ebook_logs.order_id` · `int8` · required
  >
  > Points at [`order_links`](#order_links).

### vendor_history
<!-- orbit
id: "vendor_history"
icon: "🧷"
collapsed: true
-->
> `vendor_history` · table in Reports
>
> One row per vendor, with 20 columns and about 744 thousand rows.
>
> **Belongs to** [`series_links`](#series_links) · [`card_queues`](#card_queues) · [`room_counts`](#room_counts)

- 🔑 id <!-- orbit status: "primary key" -->
  > `vendor_history.id` · `int8` · required
- 🔢 budget_code
  > `vendor_history.budget_code` · `int4` · required
- 🔤 license_title
  > `vendor_history.license_title` · `varchar` · optional
- 🕒 role_public
  > `vendor_history.role_public` · `timestamp` · required
- ☑️ hold_created
  > `vendor_history.hold_created` · `bool` · required
- 🔢 loan_count
  > `vendor_history.loan_count` · `int4` · required
- 🔗 events_id <!-- orbit link: "#events", status: "foreign key" -->
  > `vendor_history.events_id` · `int8` · required
  >
  > Points at [`events`](#events).
- 🔤 member_priority
  > `vendor_history.member_priority` · `varchar` · required
- ☑️ member_ended
  > `vendor_history.member_ended` · `bool` · required
- ☑️ vendor_total
  > `vendor_history.vendor_total` · `bool` · required
- ☑️ book_started
  > `vendor_history.book_started` · `bool` · required
- 🕒 book_title
  > `vendor_history.book_title` · `timestamp` · optional
- 🔤 visit_format
  > `vendor_history.visit_format` · `varchar` · required
- 🔢 budget_started
  > `vendor_history.budget_started` · `int4` · required
- 🔢 order_updated
  > `vendor_history.order_updated` · `int4` · required
- 🔤 report_started
  > `vendor_history.report_started` · `varchar` · required
- 🔤 report_region
  > `vendor_history.report_region` · `varchar` · optional
- 🔤 budget_total
  > `vendor_history.budget_total` · `varchar` · optional
- ☑️ notice_code
  > `vendor_history.notice_code` · `bool` · optional
- 🕒 license_position
  > `vendor_history.license_position` · `timestamp` · optional

### device_items
<!-- orbit
id: "device_items"
icon: "🗃️"
collapsed: true
-->
> `device_items` · table in Reports
>
> One row per device, with 9 columns and about 649 thousand rows.
>
> **Belongs to** [`vendor_items`](#vendor_items) · [`notice_requests`](#notice_requests) · [`vendor_history`](#vendor_history) · [`order_logs`](#order_logs)

- 🔑 id <!-- orbit status: "primary key" -->
  > `device_items.id` · `int8` · required
- 🧩 room_active
  > `device_items.room_active` · `jsonb` · optional
- 🧩 role_created
  > `device_items.role_created` · `jsonb` · required
- 🕒 report_ended
  > `device_items.report_ended` · `timestamp` · optional
- 🔗 vendor_id <!-- orbit link: "#vendor_counts", status: "foreign key" -->
  > `device_items.vendor_id` · `int8` · required
  >
  > Points at [`vendor_counts`](#vendor_counts).
- ☑️ report_language
  > `device_items.report_language` · `bool` · optional
- 🔢 book_code
  > `device_items.book_code` · `int4` · required
- 🔤 role_state
  > `device_items.role_state` · `varchar` · optional
- 🧩 loan_title
  > `device_items.loan_title` · `jsonb` · optional

### visits
<!-- orbit
id: "visits"
icon: "📗"
collapsed: true
-->
> `visits` · table in Reports
>
> One row per visits, with 4 columns and about 220 thousand rows.
>
> **Belongs to** [`author_links_3`](#author_links_3) · [`room_counts`](#room_counts) · [`audit_links`](#audit_links) · [`series_history`](#series_history)

- 🔑 id <!-- orbit status: "primary key" -->
  > `visits.id` · `int8` · required
- 🔢 donation_kind
  > `visits.donation_kind` · `int4` · required
- ☑️ loan_reviewed
  > `visits.loan_reviewed` · `bool` · optional
- 🔢 repair_active
  > `visits.repair_active` · `int4` · optional

### donation_history
<!-- orbit
id: "donation_history"
icon: "📇"
collapsed: true
-->
> `donation_history` · table in Reports
>
> One row per donation, with 16 columns and about 652 thousand rows.
>
> **Belongs to** [`report_links`](#report_links)

- 🔑 id <!-- orbit status: "primary key" -->
  > `donation_history.id` · `int8` · required
- 🔗 hold_id <!-- orbit link: "#hold_logs", status: "foreign key" -->
  > `donation_history.hold_id` · `int8` · required
  >
  > Points at [`hold_logs`](#hold_logs).
- 🔢 repair_state
  > `donation_history.repair_state` · `int4` · optional
- 🔤 audit_notes
  > `donation_history.audit_notes` · `varchar` · optional
- ☑️ tag_name
  > `donation_history.tag_name` · `bool` · required
- 🔤 copy_title
  > `donation_history.copy_title` · `varchar` · optional
- 🕒 book_code
  > `donation_history.book_code` · `timestamp` · required
- 🔤 audit_notes_2
  > `donation_history.audit_notes_2` · `varchar` · optional
- 🕒 hold_started
  > `donation_history.hold_started` · `timestamp` · required
- 🧩 tag_status
  > `donation_history.tag_status` · `jsonb` · required
- 🔤 author_format
  > `donation_history.author_format` · `varchar` · required
- 🔤 review_state
  > `donation_history.review_state` · `varchar` · required
- 🔢 visit_kind
  > `donation_history.visit_kind` · `int4` · required
- 🕒 report_total
  > `donation_history.report_total` · `timestamp` · optional
- 🔗 room_id <!-- orbit link: "#room_logs", status: "foreign key" -->
  > `donation_history.room_id` · `int8` · required
  >
  > Points at [`room_logs`](#room_logs).
- 🧩 visit_count
  > `donation_history.visit_count` · `jsonb` · optional

### hold_links_2
<!-- orbit
id: "hold_links_2"
icon: "🗃️"
collapsed: true
-->
> `hold_links_2` · table in Reports
>
> One row per hold, with 5 columns and about 648 thousand rows.
>
> **Belongs to** [`hold_links`](#hold_links)

- 🔑 id <!-- orbit status: "primary key" -->
  > `hold_links_2.id` · `int8` · required
- 🔤 audit_state
  > `hold_links_2.audit_state` · `varchar` · optional
- 🔤 loan_source
  > `hold_links_2.loan_source` · `varchar` · required
- ☑️ fee_ended
  > `hold_links_2.fee_ended` · `bool` · required
- 🕒 book_ended
  > `hold_links_2.book_ended` · `timestamp` · required

### license_queues_2
<!-- orbit
id: "license_queues_2"
icon: "🧮"
collapsed: true
-->
> `license_queues_2` · table in Reports
>
> One row per license, with 5 columns and about 292 thousand rows.
>
> **Belongs to** [`member_history`](#member_history) · [`donation_history`](#donation_history)

- 🔑 id <!-- orbit status: "primary key" -->
  > `license_queues_2.id` · `int8` · required
- 🔢 audit_state
  > `license_queues_2.audit_state` · `int4` · required
- 🔢 device_active
  > `license_queues_2.device_active` · `int4` · required
- ☑️ copy_format
  > `license_queues_2.copy_format` · `bool` · required
- 🧩 series_public
  > `license_queues_2.series_public` · `jsonb` · required

### rooms
<!-- orbit
id: "rooms"
icon: "📇"
collapsed: true
-->
> `rooms` · table in Reports
>
> One row per rooms, with 23 columns and about 49 thousand rows.
>
> **Belongs to** [`visit_queues`](#visit_queues)

- 🔑 id <!-- orbit status: "primary key" -->
  > `rooms.id` · `int8` · required
- 🔤 donation_external
  > `rooms.donation_external` · `varchar` · optional
- 🔤 role_started
  > `rooms.role_started` · `varchar` · required
- 🔤 visit_priority
  > `rooms.visit_priority` · `varchar` · required
- 🧩 report_format
  > `rooms.report_format` · `jsonb` · optional
- 🔗 event_id <!-- orbit link: "#event_history", status: "foreign key" -->
  > `rooms.event_id` · `int8` · required
  >
  > Points at [`event_history`](#event_history).
- 🔢 book_amount
  > `rooms.book_amount` · `int4` · required
- 🧩 role_label
  > `rooms.role_label` · `jsonb` · required
- 🧩 series_kind
  > `rooms.series_kind` · `jsonb` · optional
- 🕒 ebook_reviewed
  > `rooms.ebook_reviewed` · `timestamp` · optional
- 🕒 audit_public
  > `rooms.audit_public` · `timestamp` · optional
- 🔤 copy_updated
  > `rooms.copy_updated` · `varchar` · optional
- 🔢 device_state
  > `rooms.device_state` · `int4` · optional
- 🧩 card_total
  > `rooms.card_total` · `jsonb` · optional
- 🕒 hold_kind
  > `rooms.hold_kind` · `timestamp` · optional
- ☑️ loan_amount
  > `rooms.loan_amount` · `bool` · required
- 🧩 book_position
  > `rooms.book_position` · `jsonb` · optional
- 🔢 device_format
  > `rooms.device_format` · `int4` · optional
- 🔢 author_public
  > `rooms.author_public` · `int4` · optional
- 🔢 role_public
  > `rooms.role_public` · `int4` · required
- 🔢 role_position
  > `rooms.role_position` · `int4` · required
- 🕒 shelf_label
  > `rooms.shelf_label` · `timestamp` · required
- ☑️ budget_code
  > `rooms.budget_code` · `bool` · optional

### loan_logs
<!-- orbit
id: "loan_logs"
icon: "📇"
collapsed: true
-->
> `loan_logs` · table in Reports
>
> One row per loan, with 11 columns and about 524 thousand rows.
>
> **Belongs to** [`order_logs`](#order_logs) · [`audit_links_4`](#audit_links_4) · [`notice_counts`](#notice_counts)

- 🔑 id <!-- orbit status: "primary key" -->
  > `loan_logs.id` · `int8` · required
- ☑️ card_language
  > `loan_logs.card_language` · `bool` · required
- 🕒 book_count
  > `loan_logs.book_count` · `timestamp` · optional
- ☑️ vendor_count
  > `loan_logs.vendor_count` · `bool` · required
- 🕒 author_external
  > `loan_logs.author_external` · `timestamp` · required
- 🔗 book_id <!-- orbit link: "#book_items", status: "foreign key" -->
  > `loan_logs.book_id` · `int8` · required
  >
  > Points at [`book_items`](#book_items).
- 🔢 device_label
  > `loan_logs.device_label` · `int4` · optional
- 🧩 audit_created
  > `loan_logs.audit_created` · `jsonb` · required
- 🧩 vendor_title
  > `loan_logs.vendor_title` · `jsonb` · optional
- ☑️ ebook_code
  > `loan_logs.ebook_code` · `bool` · required
- 🔗 event_id <!-- orbit link: "#event_history", status: "foreign key" -->
  > `loan_logs.event_id` · `int8` · required
  >
  > Points at [`event_history`](#event_history).

### review_queues
<!-- orbit
id: "review_queues"
icon: "📇"
collapsed: true
-->
> `review_queues` · table in Reports
>
> One row per review, with 20 columns and about 814 thousand rows.
>
> **Belongs to** [`member_counts_2`](#member_counts_2)

- 🔑 id <!-- orbit status: "primary key" -->
  > `review_queues.id` · `int8` · required
- 🧩 vendor_label
  > `review_queues.vendor_label` · `jsonb` · required
- 🔢 donation_archived
  > `review_queues.donation_archived` · `int4` · required
- 🔗 donation_id <!-- orbit link: "#donation_requests", status: "foreign key" -->
  > `review_queues.donation_id` · `int8` · required
  >
  > Points at [`donation_requests`](#donation_requests).
- 🔢 event_language
  > `review_queues.event_language` · `int4` · optional
- 🔤 hold_region
  > `review_queues.hold_region` · `varchar` · required
- 🔢 staff_active
  > `review_queues.staff_active` · `int4` · optional
- 🔤 event_name
  > `review_queues.event_name` · `varchar` · required
- ☑️ staff_name
  > `review_queues.staff_name` · `bool` · optional
- 🧩 author_kind
  > `review_queues.author_kind` · `jsonb` · optional
- 🔢 staff_name_2
  > `review_queues.staff_name_2` · `int4` · optional
- 🧩 series_name
  > `review_queues.series_name` · `jsonb` · required
- 🔗 report_id <!-- orbit link: "#report_queues_2", status: "foreign key" -->
  > `review_queues.report_id` · `int8` · required
  >
  > Points at [`report_queues_2`](#report_queues_2).
- 🕒 review_region
  > `review_queues.review_region` · `timestamp` · required
- 🔗 staff_id <!-- orbit link: "#staff_items", status: "foreign key" -->
  > `review_queues.staff_id` · `int8` · required
  >
  > Points at [`staff_items`](#staff_items).
- 🔤 vendor_total
  > `review_queues.vendor_total` · `varchar` · optional
- 🔤 tag_kind
  > `review_queues.tag_kind` · `varchar` · optional
- 🔗 author_id <!-- orbit link: "#author_links_3", status: "foreign key" -->
  > `review_queues.author_id` · `int8` · required
  >
  > Points at [`author_links_3`](#author_links_3).
- 🧩 budget_count
  > `review_queues.budget_count` · `jsonb` · optional
- 🔗 staff_id_2 <!-- orbit link: "#staff_counts", status: "foreign key" -->
  > `review_queues.staff_id_2` · `int8` · required
  >
  > Points at [`staff_counts`](#staff_counts).

### tag_settings_2
<!-- orbit
id: "tag_settings_2"
icon: "🧷"
collapsed: true
-->
> `tag_settings_2` · table in Reports
>
> One row per tag, with 18 columns and about 409 thousand rows.
>
> **Belongs to** [`visit_queues`](#visit_queues)

- 🔑 id <!-- orbit status: "primary key" -->
  > `tag_settings_2.id` · `int8` · required
- 🔗 book_id <!-- orbit link: "#book_items", status: "foreign key" -->
  > `tag_settings_2.book_id` · `int8` · required
  >
  > Points at [`book_items`](#book_items).
- 🧩 card_region
  > `tag_settings_2.card_region` · `jsonb` · required
- 🔗 card_id <!-- orbit link: "#card_queues", status: "foreign key" -->
  > `tag_settings_2.card_id` · `int8` · required
  >
  > Points at [`card_queues`](#card_queues).
- 🔢 event_updated
  > `tag_settings_2.event_updated` · `int4` · optional
- ☑️ loan_external
  > `tag_settings_2.loan_external` · `bool` · optional
- 🕒 hold_kind
  > `tag_settings_2.hold_kind` · `timestamp` · required
- 🔤 device_notes
  > `tag_settings_2.device_notes` · `varchar` · optional
- ☑️ branch_active
  > `tag_settings_2.branch_active` · `bool` · required
- ☑️ member_public
  > `tag_settings_2.member_public` · `bool` · required
- 🕒 hold_ended
  > `tag_settings_2.hold_ended` · `timestamp` · required
- 🔢 payment_title
  > `tag_settings_2.payment_title` · `int4` · required
- 🧩 fee_updated
  > `tag_settings_2.fee_updated` · `jsonb` · required
- ☑️ budget_public
  > `tag_settings_2.budget_public` · `bool` · optional
- 🕒 budget_position
  > `tag_settings_2.budget_position` · `timestamp` · required
- 🔢 review_active
  > `tag_settings_2.review_active` · `int4` · optional
- ☑️ visit_active
  > `tag_settings_2.visit_active` · `bool` · required
- 🔗 shelf_id <!-- orbit link: "#shelf_counts", status: "foreign key" -->
  > `tag_settings_2.shelf_id` · `int8` · required
  >
  > Points at [`shelf_counts`](#shelf_counts).

### series_history
<!-- orbit
id: "series_history"
icon: "🧷"
collapsed: true
-->
> `series_history` · table in Reports
>
> One row per series, with 17 columns and about 267 thousand rows.
>
> **Belongs to** [`review_items`](#review_items) · [`author_history`](#author_history) · [`tag_settings_2`](#tag_settings_2) · [`member_history`](#member_history)

- 🔑 id <!-- orbit status: "primary key" -->
  > `series_history.id` · `int8` · required
- 🧩 license_kind
  > `series_history.license_kind` · `jsonb` · optional
- 🔤 book_kind
  > `series_history.book_kind` · `varchar` · required
- 🧩 repair_region
  > `series_history.repair_region` · `jsonb` · required
- 🕒 author_ended
  > `series_history.author_ended` · `timestamp` · required
- ☑️ copy_region
  > `series_history.copy_region` · `bool` · required
- 🔗 loan_id <!-- orbit link: "#loan_history", status: "foreign key" -->
  > `series_history.loan_id` · `int8` · required
  >
  > Points at [`loan_history`](#loan_history).
- 🔤 order_notes
  > `series_history.order_notes` · `varchar` · required
- ☑️ room_priority
  > `series_history.room_priority` · `bool` · required
- 🔗 event_id <!-- orbit link: "#event_history", status: "foreign key" -->
  > `series_history.event_id` · `int8` · required
  >
  > Points at [`event_history`](#event_history).
- 🧩 staff_started
  > `series_history.staff_started` · `jsonb` · required
- 🔗 ebook_id <!-- orbit link: "#ebook_queues", status: "foreign key" -->
  > `series_history.ebook_id` · `int8` · required
  >
  > Points at [`ebook_queues`](#ebook_queues).
- 🕒 ebook_started
  > `series_history.ebook_started` · `timestamp` · required
- 🔢 vendor_region
  > `series_history.vendor_region` · `int4` · optional
- ☑️ tag_code
  > `series_history.tag_code` · `bool` · optional
- 🔗 copy_id <!-- orbit link: "#copy_versions", status: "foreign key" -->
  > `series_history.copy_id` · `int8` · required
  >
  > Points at [`copy_versions`](#copy_versions).
- 🧩 audit_archived
  > `series_history.audit_archived` · `jsonb` · required

## 🔐 Staff & Access
<!-- orbit
id: "area-staff-access"
color: "#9333EA"
-->
> 12 tables. Largest: [`order_links`](#order_links), [`license_logs_2`](#license_logs_2), [`payment_requests`](#payment_requests).
>
> **Linked to** [`donations`](#donations) · [`review_logs`](#review_logs) · [`budget_queues`](#budget_queues)

### order_links
<!-- orbit
id: "order_links"
icon: "📇"
collapsed: true
-->
> `order_links` · table in Staff & Access
>
> One row per order, with 18 columns and about 360 thousand rows.
>
> **Belongs to** [`loan_settings`](#loan_settings) · [`review_logs`](#review_logs) · [`staff_counts`](#staff_counts)

- 🔑 id <!-- orbit status: "primary key" -->
  > `order_links.id` · `int8` · required
- 🔢 device_external
  > `order_links.device_external` · `int4` · optional
- 🧩 report_notes
  > `order_links.report_notes` · `jsonb` · optional
- 🧩 repair_code
  > `order_links.repair_code` · `jsonb` · required
- 🔗 fee_id <!-- orbit link: "#fee_requests", status: "foreign key" -->
  > `order_links.fee_id` · `int8` · required
  >
  > Points at [`fee_requests`](#fee_requests).
- ☑️ notice_language
  > `order_links.notice_language` · `bool` · optional
- 🔤 branch_public
  > `order_links.branch_public` · `varchar` · required
- 🕒 fee_source
  > `order_links.fee_source` · `timestamp` · required
- 🔢 card_archived
  > `order_links.card_archived` · `int4` · optional
- 🧩 card_external
  > `order_links.card_external` · `jsonb` · optional
- 🕒 room_active
  > `order_links.room_active` · `timestamp` · optional
- 🕒 member_total
  > `order_links.member_total` · `timestamp` · required
- 🔤 budget_title
  > `order_links.budget_title` · `varchar` · optional
- 🧩 room_count
  > `order_links.room_count` · `jsonb` · optional
- 🧩 room_started
  > `order_links.room_started` · `jsonb` · required
- ☑️ ebook_label
  > `order_links.ebook_label` · `bool` · optional
- ☑️ audit_archived
  > `order_links.audit_archived` · `bool` · optional
- 🔗 order_id <!-- orbit link: "#order_versions", status: "foreign key" -->
  > `order_links.order_id` · `int8` · required
  >
  > Points at [`order_versions`](#order_versions).

### license_logs_2
<!-- orbit
id: "license_logs_2"
icon: "📗"
collapsed: true
-->
> `license_logs_2` · table in Staff & Access
>
> One row per license, with 19 columns and about 515 thousand rows.
>
> **Belongs to** [`author_links_3`](#author_links_3) · [`order_items`](#order_items) · [`series_requests`](#series_requests) · [`room_counts_2`](#room_counts_2)

- 🔑 id <!-- orbit status: "primary key" -->
  > `license_logs_2.id` · `int8` · required
- ☑️ member_language
  > `license_logs_2.member_language` · `bool` · optional
- 🔗 card_id <!-- orbit link: "#card_links_2", status: "foreign key" -->
  > `license_logs_2.card_id` · `int8` · required
  >
  > Points at [`card_links_2`](#card_links_2).
- 🔢 member_reviewed
  > `license_logs_2.member_reviewed` · `int4` · optional
- 🧩 book_ended
  > `license_logs_2.book_ended` · `jsonb` · optional
- 🔗 report_id <!-- orbit link: "#report_queues", status: "foreign key" -->
  > `license_logs_2.report_id` · `int8` · required
  >
  > Points at [`report_queues`](#report_queues).
- 🕒 order_notes
  > `license_logs_2.order_notes` · `timestamp` · required
- 🔗 visit_id <!-- orbit link: "#visit_links", status: "foreign key" -->
  > `license_logs_2.visit_id` · `int8` · required
  >
  > Points at [`visit_links`](#visit_links).
- 🔤 budget_total
  > `license_logs_2.budget_total` · `varchar` · required
- 🧩 tag_position
  > `license_logs_2.tag_position` · `jsonb` · optional
- 🔢 tag_public
  > `license_logs_2.tag_public` · `int4` · required
- ☑️ audit_reviewed
  > `license_logs_2.audit_reviewed` · `bool` · required
- ☑️ card_code
  > `license_logs_2.card_code` · `bool` · optional
- 🕒 card_label
  > `license_logs_2.card_label` · `timestamp` · optional
- ☑️ book_state
  > `license_logs_2.book_state` · `bool` · required
- 🔢 loan_kind
  > `license_logs_2.loan_kind` · `int4` · optional
- ☑️ order_count
  > `license_logs_2.order_count` · `bool` · optional
- 🧩 event_state
  > `license_logs_2.event_state` · `jsonb` · optional
- 🔤 fee_format
  > `license_logs_2.fee_format` · `varchar` · required

### payment_requests
<!-- orbit
id: "payment_requests"
icon: "🗃️"
collapsed: true
-->
> `payment_requests` · table in Staff & Access
>
> One row per payment, with 21 columns and about 372 thousand rows.
>
> **Belongs to** [`card_logs`](#card_logs) · [`license_versions`](#license_versions) · [`vendor_counts`](#vendor_counts) · [`member_counts`](#member_counts)

- 🔑 id <!-- orbit status: "primary key" -->
  > `payment_requests.id` · `int8` · required
- ☑️ review_position
  > `payment_requests.review_position` · `bool` · required
- ☑️ donation_label
  > `payment_requests.donation_label` · `bool` · optional
- 🕒 vendor_notes
  > `payment_requests.vendor_notes` · `timestamp` · optional
- 🔗 donation_id <!-- orbit link: "#donation_history", status: "foreign key" -->
  > `payment_requests.donation_id` · `int8` · required
  >
  > Points at [`donation_history`](#donation_history).
- 🔢 order_notes
  > `payment_requests.order_notes` · `int4` · optional
- 🔢 visit_code
  > `payment_requests.visit_code` · `int4` · optional
- 🧩 hold_code
  > `payment_requests.hold_code` · `jsonb` · optional
- 🔢 order_code
  > `payment_requests.order_code` · `int4` · required
- 🔗 events_id <!-- orbit link: "#events", status: "foreign key" -->
  > `payment_requests.events_id` · `int8` · required
  >
  > Points at [`events`](#events).
- 🔗 payment_id <!-- orbit link: "#payment_requests", status: "foreign key" -->
  > `payment_requests.payment_id` · `int8` · required
  >
  > Points at [`payment_requests`](#payment_requests).
- ☑️ member_active
  > `payment_requests.member_active` · `bool` · required
- 🔢 staff_state
  > `payment_requests.staff_state` · `int4` · optional
- 🕒 budget_label
  > `payment_requests.budget_label` · `timestamp` · optional
- 🔤 member_reviewed
  > `payment_requests.member_reviewed` · `varchar` · optional
- 🔤 card_status
  > `payment_requests.card_status` · `varchar` · optional
- 🔤 budget_language
  > `payment_requests.budget_language` · `varchar` · required
- 🧩 review_language
  > `payment_requests.review_language` · `jsonb` · optional
- 🕒 event_external
  > `payment_requests.event_external` · `timestamp` · required
- 🔤 review_kind
  > `payment_requests.review_kind` · `varchar` · required
- 🕒 book_state
  > `payment_requests.book_state` · `timestamp` · optional

### report_links
<!-- orbit
id: "report_links"
icon: "🗃️"
collapsed: true
-->
> `report_links` · table in Staff & Access
>
> One row per report, with 17 columns and about 417 thousand rows.
>
> **Belongs to** [`order_items`](#order_items) · [`audit_links`](#audit_links) · [`room_counts`](#room_counts)

- 🔑 id <!-- orbit status: "primary key" -->
  > `report_links.id` · `int8` · required
- ☑️ staff_public
  > `report_links.staff_public` · `bool` · optional
- 🧩 card_region
  > `report_links.card_region` · `jsonb` · optional
- 🕒 donation_status
  > `report_links.donation_status` · `timestamp` · required
- 🔗 report_id <!-- orbit link: "#report_queues", status: "foreign key" -->
  > `report_links.report_id` · `int8` · required
  >
  > Points at [`report_queues`](#report_queues).
- 🕒 tag_state
  > `report_links.tag_state` · `timestamp` · optional
- 🕒 review_language
  > `report_links.review_language` · `timestamp` · optional
- 🔗 seriess_id <!-- orbit link: "#seriess", status: "foreign key" -->
  > `report_links.seriess_id` · `int8` · required
  >
  > Points at [`seriess`](#seriess).
- 🔤 device_total
  > `report_links.device_total` · `varchar` · required
- ☑️ member_source
  > `report_links.member_source` · `bool` · optional
- 🧩 loan_priority
  > `report_links.loan_priority` · `jsonb` · optional
- 🧩 copy_amount
  > `report_links.copy_amount` · `jsonb` · optional
- ☑️ book_source
  > `report_links.book_source` · `bool` · optional
- 🔤 donation_language
  > `report_links.donation_language` · `varchar` · optional
- 🧩 donation_region
  > `report_links.donation_region` · `jsonb` · required
- 🔤 copy_count
  > `report_links.copy_count` · `varchar` · required
- 🔗 donation_id <!-- orbit link: "#donation_links", status: "foreign key" -->
  > `report_links.donation_id` · `int8` · required
  >
  > Points at [`donation_links`](#donation_links).

### member_history
<!-- orbit
id: "member_history"
icon: "📇"
collapsed: true
-->
> `member_history` · table in Staff & Access
>
> One row per member, with 23 columns and about 91 thousand rows.
>
> **Belongs to** [`staff_settings`](#staff_settings) · [`staff_counts`](#staff_counts) · [`vendor_history`](#vendor_history) · [`hold_links_2`](#hold_links_2)

- 🔑 id <!-- orbit status: "primary key" -->
  > `member_history.id` · `int8` · required
- 🔢 budget_notes
  > `member_history.budget_notes` · `int4` · required
- 🧩 staff_priority
  > `member_history.staff_priority` · `jsonb` · optional
- 🕒 order_public
  > `member_history.order_public` · `timestamp` · required
- 🧩 review_amount
  > `member_history.review_amount` · `jsonb` · required
- 🕒 member_created
  > `member_history.member_created` · `timestamp` · optional
- 🕒 report_amount
  > `member_history.report_amount` · `timestamp` · required
- 🧩 visit_language
  > `member_history.visit_language` · `jsonb` · required
- 🧩 copy_active
  > `member_history.copy_active` · `jsonb` · optional
- 🕒 audit_status
  > `member_history.audit_status` · `timestamp` · required
- 🔤 repair_notes
  > `member_history.repair_notes` · `varchar` · optional
- ☑️ order_total
  > `member_history.order_total` · `bool` · required
- ☑️ license_active
  > `member_history.license_active` · `bool` · optional
- ☑️ card_reviewed
  > `member_history.card_reviewed` · `bool` · optional
- 🕒 loan_active
  > `member_history.loan_active` · `timestamp` · required
- ☑️ loan_total
  > `member_history.loan_total` · `bool` · optional
- 🧩 copy_region
  > `member_history.copy_region` · `jsonb` · required
- 🔤 audit_ended
  > `member_history.audit_ended` · `varchar` · required
- ☑️ payment_code
  > `member_history.payment_code` · `bool` · optional
- 🧩 member_external
  > `member_history.member_external` · `jsonb` · optional
- 🔤 review_format
  > `member_history.review_format` · `varchar` · optional
- 🔤 ebook_source
  > `member_history.ebook_source` · `varchar` · required
- 🔤 branch_ended
  > `member_history.branch_ended` · `varchar` · optional

### vendor_settings
<!-- orbit
id: "vendor_settings"
icon: "📇"
collapsed: true
-->
> `vendor_settings` · table in Staff & Access
>
> One row per vendor, with 10 columns and about 237 thousand rows.
>
> **Belongs to** [`fee_history`](#fee_history) · [`notice_counts_2`](#notice_counts_2) · [`shelfs`](#shelfs)

- 🔑 id <!-- orbit status: "primary key" -->
  > `vendor_settings.id` · `int8` · required
- 🔤 visit_updated
  > `vendor_settings.visit_updated` · `varchar` · required
- 🔗 audit_id <!-- orbit link: "#audit_queues", status: "foreign key" -->
  > `vendor_settings.audit_id` · `int8` · required
  >
  > Points at [`audit_queues`](#audit_queues).
- 🔗 vendor_id <!-- orbit link: "#vendor_items", status: "foreign key" -->
  > `vendor_settings.vendor_id` · `int8` · required
  >
  > Points at [`vendor_items`](#vendor_items).
- 🕒 role_archived
  > `vendor_settings.role_archived` · `timestamp` · optional
- 🧩 vendor_reviewed
  > `vendor_settings.vendor_reviewed` · `jsonb` · required
- 🔗 copy_id <!-- orbit link: "#copy_items", status: "foreign key" -->
  > `vendor_settings.copy_id` · `int8` · required
  >
  > Points at [`copy_items`](#copy_items).
- 🔗 budgets_id <!-- orbit link: "#budgets", status: "foreign key" -->
  > `vendor_settings.budgets_id` · `int8` · required
  >
  > Points at [`budgets`](#budgets).
- 🔗 series_id <!-- orbit link: "#series_counts_2", status: "foreign key" -->
  > `vendor_settings.series_id` · `int8` · required
  >
  > Points at [`series_counts_2`](#series_counts_2).
- 🧩 series_reviewed
  > `vendor_settings.series_reviewed` · `jsonb` · optional

### copy_counts
<!-- orbit
id: "copy_counts"
icon: "📇"
collapsed: true
-->
> `copy_counts` · table in Staff & Access
>
> One row per copy, with 13 columns and about 414 thousand rows.
>
> **Belongs to** [`room_counts_2`](#room_counts_2)

- 🔑 id <!-- orbit status: "primary key" -->
  > `copy_counts.id` · `int8` · required
- 🕒 staff_public
  > `copy_counts.staff_public` · `timestamp` · required
- 🧩 author_archived
  > `copy_counts.author_archived` · `jsonb` · optional
- 🕒 audit_archived
  > `copy_counts.audit_archived` · `timestamp` · optional
- 🕒 ebook_position
  > `copy_counts.ebook_position` · `timestamp` · optional
- 🔢 copy_notes
  > `copy_counts.copy_notes` · `int4` · optional
- ☑️ ebook_kind
  > `copy_counts.ebook_kind` · `bool` · optional
- 🔗 payment_id <!-- orbit link: "#payment_settings", status: "foreign key" -->
  > `copy_counts.payment_id` · `int8` · required
  >
  > Points at [`payment_settings`](#payment_settings).
- 🔗 staff_id <!-- orbit link: "#staff_counts", status: "foreign key" -->
  > `copy_counts.staff_id` · `int8` · required
  >
  > Points at [`staff_counts`](#staff_counts).
- 🔤 audit_label
  > `copy_counts.audit_label` · `varchar` · required
- 🧩 hold_title
  > `copy_counts.hold_title` · `jsonb` · optional
- ☑️ series_count
  > `copy_counts.series_count` · `bool` · optional
- 🔤 payment_archived
  > `copy_counts.payment_archived` · `varchar` · optional

### order_logs
<!-- orbit
id: "order_logs"
icon: "🗂️"
collapsed: true
-->
> `order_logs` · table in Staff & Access
>
> One row per order, with 21 columns and about 184 thousand rows.
>
> **Belongs to** [`hold_links_2`](#hold_links_2) · [`series_counts_2`](#series_counts_2) · [`audit_settings`](#audit_settings)

- 🔑 id <!-- orbit status: "primary key" -->
  > `order_logs.id` · `int8` · required
- 🕒 branch_updated
  > `order_logs.branch_updated` · `timestamp` · optional
- 🕒 report_started
  > `order_logs.report_started` · `timestamp` · optional
- 🧩 device_count
  > `order_logs.device_count` · `jsonb` · optional
- 🔤 branch_amount
  > `order_logs.branch_amount` · `varchar` · required
- 🕒 donation_total
  > `order_logs.donation_total` · `timestamp` · required
- 🧩 book_amount
  > `order_logs.book_amount` · `jsonb` · optional
- 🧩 fee_total
  > `order_logs.fee_total` · `jsonb` · required
- 🔢 donation_ended
  > `order_logs.donation_ended` · `int4` · optional
- 🕒 staff_region
  > `order_logs.staff_region` · `timestamp` · required
- ☑️ budget_code
  > `order_logs.budget_code` · `bool` · required
- ☑️ copy_kind
  > `order_logs.copy_kind` · `bool` · required
- 🔢 tag_notes
  > `order_logs.tag_notes` · `int4` · required
- 🔤 audit_external
  > `order_logs.audit_external` · `varchar` · required
- 🕒 audit_title
  > `order_logs.audit_title` · `timestamp` · required
- 🧩 vendor_language
  > `order_logs.vendor_language` · `jsonb` · optional
- 🔢 tag_total
  > `order_logs.tag_total` · `int4` · optional
- 🕒 vendor_reviewed
  > `order_logs.vendor_reviewed` · `timestamp` · optional
- 🔤 notice_language
  > `order_logs.notice_language` · `varchar` · optional
- 🔗 vendor_id <!-- orbit link: "#vendor_settings", status: "foreign key" -->
  > `order_logs.vendor_id` · `int8` · required
  >
  > Points at [`vendor_settings`](#vendor_settings).
- 🧩 tag_region
  > `order_logs.tag_region` · `jsonb` · optional

### report_versions
<!-- orbit
id: "report_versions"
icon: "📇"
collapsed: true
-->
> `report_versions` · table in Staff & Access
>
> One row per report, with 7 columns and about 266 thousand rows.
>
> **Belongs to** [`series_links`](#series_links) · [`loan_counts`](#loan_counts) · [`series_counts_2`](#series_counts_2)

- 🔑 id <!-- orbit status: "primary key" -->
  > `report_versions.id` · `int8` · required
- 🔤 ebook_notes
  > `report_versions.ebook_notes` · `varchar` · required
- 🔤 staff_started
  > `report_versions.staff_started` · `varchar` · required
- 🧩 budget_source
  > `report_versions.budget_source` · `jsonb` · required
- 🕒 order_language
  > `report_versions.order_language` · `timestamp` · required
- ☑️ visit_title
  > `report_versions.visit_title` · `bool` · optional
- 🧩 card_ended
  > `report_versions.card_ended` · `jsonb` · required

### holds_2
<!-- orbit
id: "holds_2"
icon: "🧷"
collapsed: true
-->
> `holds_2` · table in Staff & Access
>
> One row per holds, with 18 columns and about 340 thousand rows.
>
> **Belongs to** [`order_logs`](#order_logs) · [`report_queues_2`](#report_queues_2) · [`card_queues`](#card_queues) · [`tag_counts`](#tag_counts)

- 🔑 id <!-- orbit status: "primary key" -->
  > `holds_2.id` · `int8` · required
- 🔗 device_id <!-- orbit link: "#device_settings", status: "foreign key" -->
  > `holds_2.device_id` · `int8` · required
  >
  > Points at [`device_settings`](#device_settings).
- 🧩 room_archived
  > `holds_2.room_archived` · `jsonb` · optional
- ☑️ report_public
  > `holds_2.report_public` · `bool` · required
- 🔢 branch_format
  > `holds_2.branch_format` · `int4` · optional
- 🔤 order_status
  > `holds_2.order_status` · `varchar` · optional
- 🕒 donation_updated
  > `holds_2.donation_updated` · `timestamp` · optional
- ☑️ shelf_kind
  > `holds_2.shelf_kind` · `bool` · optional
- 🔗 copy_id <!-- orbit link: "#copy_items", status: "foreign key" -->
  > `holds_2.copy_id` · `int8` · required
  >
  > Points at [`copy_items`](#copy_items).
- 🔢 payment_language
  > `holds_2.payment_language` · `int4` · optional
- 🧩 member_source
  > `holds_2.member_source` · `jsonb` · optional
- 🕒 room_external
  > `holds_2.room_external` · `timestamp` · required
- 🔢 shelf_active
  > `holds_2.shelf_active` · `int4` · optional
- ☑️ payment_public
  > `holds_2.payment_public` · `bool` · required
- 🧩 shelf_total
  > `holds_2.shelf_total` · `jsonb` · required
- 🔤 donation_code
  > `holds_2.donation_code` · `varchar` · required
- 🔤 series_priority
  > `holds_2.series_priority` · `varchar` · optional
- 🔗 vendor_id <!-- orbit link: "#vendor_settings", status: "foreign key" -->
  > `holds_2.vendor_id` · `int8` · required
  >
  > Points at [`vendor_settings`](#vendor_settings).

### branchs
<!-- orbit
id: "branchs"
icon: "🗂️"
collapsed: true
-->
> `branchs` · table in Staff & Access
>
> One row per branchs, with 14 columns and about 619 thousand rows.
>
> **Belongs to** [`staff_counts`](#staff_counts) · [`staff_logs`](#staff_logs) · [`room_requests`](#room_requests)

- 🔑 id <!-- orbit status: "primary key" -->
  > `branchs.id` · `int8` · required
- 🕒 report_ended
  > `branchs.report_ended` · `timestamp` · required
- 🕒 staff_archived
  > `branchs.staff_archived` · `timestamp` · required
- 🔗 report_id <!-- orbit link: "#report_items", status: "foreign key" -->
  > `branchs.report_id` · `int8` · required
  >
  > Points at [`report_items`](#report_items).
- 🔤 member_active
  > `branchs.member_active` · `varchar` · required
- 🔤 hold_source
  > `branchs.hold_source` · `varchar` · optional
- 🔢 notice_source
  > `branchs.notice_source` · `int4` · required
- ☑️ hold_format
  > `branchs.hold_format` · `bool` · optional
- ☑️ order_source
  > `branchs.order_source` · `bool` · required
- 🕒 copy_status
  > `branchs.copy_status` · `timestamp` · optional
- 🔤 review_format
  > `branchs.review_format` · `varchar` · required
- 🔤 vendor_language
  > `branchs.vendor_language` · `varchar` · optional
- 🧩 member_label
  > `branchs.member_label` · `jsonb` · optional
- 🔤 series_total
  > `branchs.series_total` · `varchar` · optional

### series_links
<!-- orbit
id: "series_links"
icon: "🗃️"
collapsed: true
-->
> `series_links` · table in Staff & Access
>
> One row per series, with 23 columns and about 436 thousand rows.
>
> **Belongs to** [`book_links`](#book_links)

- 🔑 id <!-- orbit status: "primary key" -->
  > `series_links.id` · `int8` · required
- 🔤 shelf_ended
  > `series_links.shelf_ended` · `varchar` · required
- ☑️ visit_position
  > `series_links.visit_position` · `bool` · optional
- 🕒 report_priority
  > `series_links.report_priority` · `timestamp` · required
- 🧩 author_kind
  > `series_links.author_kind` · `jsonb` · optional
- ☑️ donation_format
  > `series_links.donation_format` · `bool` · required
- 🔤 shelf_created
  > `series_links.shelf_created` · `varchar` · required
- 🧩 repair_public
  > `series_links.repair_public` · `jsonb` · optional
- ☑️ event_source
  > `series_links.event_source` · `bool` · required
- ☑️ fee_count
  > `series_links.fee_count` · `bool` · required
- 🔤 notice_started
  > `series_links.notice_started` · `varchar` · optional
- ☑️ vendor_position
  > `series_links.vendor_position` · `bool` · optional
- 🕒 device_count
  > `series_links.device_count` · `timestamp` · required
- 🔗 donation_id <!-- orbit link: "#donation_links", status: "foreign key" -->
  > `series_links.donation_id` · `int8` · required
  >
  > Points at [`donation_links`](#donation_links).
- 🕒 ebook_reviewed
  > `series_links.ebook_reviewed` · `timestamp` · optional
- ☑️ license_kind
  > `series_links.license_kind` · `bool` · optional
- 🔢 review_archived
  > `series_links.review_archived` · `int4` · required
- 🔢 member_status
  > `series_links.member_status` · `int4` · optional
- ☑️ loan_source
  > `series_links.loan_source` · `bool` · required
- 🔗 member_id <!-- orbit link: "#member_versions", status: "foreign key" -->
  > `series_links.member_id` · `int8` · required
  >
  > Points at [`member_versions`](#member_versions).
- 🔢 tag_public
  > `series_links.tag_public` · `int4` · optional
- 🕒 book_region
  > `series_links.book_region` · `timestamp` · optional
- 🔢 hold_position
  > `series_links.hold_position` · `int4` · required

## 📨 Notices
<!-- orbit
id: "area-notices"
color: "#DB2777"
-->
> 10 tables. Largest: [`ebook_requests`](#ebook_requests), [`card_links_2`](#card_links_2), [`review_counts`](#review_counts).
>
> **Linked to** [`order_items`](#order_items) · [`hold_links_2`](#hold_links_2) · [`book_links`](#book_links)

### ebook_requests
<!-- orbit
id: "ebook_requests"
icon: "🗂️"
collapsed: true
-->
> `ebook_requests` · table in Notices
>
> One row per ebook, with 6 columns and about 437 thousand rows.
>
> **Belongs to** [`review_logs`](#review_logs) · [`card_links_2`](#card_links_2) · [`event_history`](#event_history) · [`audit_queues`](#audit_queues)

- 🔑 id <!-- orbit status: "primary key" -->
  > `ebook_requests.id` · `int8` · required
- 🔤 series_code
  > `ebook_requests.series_code` · `varchar` · optional
- 🔗 branch_id <!-- orbit link: "#branch_links", status: "foreign key" -->
  > `ebook_requests.branch_id` · `int8` · required
  >
  > Points at [`branch_links`](#branch_links).
- 🧩 fee_code
  > `ebook_requests.fee_code` · `jsonb` · required
- ☑️ member_region
  > `ebook_requests.member_region` · `bool` · required
- 🔗 visit_id <!-- orbit link: "#visit_items", status: "foreign key" -->
  > `ebook_requests.visit_id` · `int8` · required
  >
  > Points at [`visit_items`](#visit_items).

### card_links_2
<!-- orbit
id: "card_links_2"
icon: "🧷"
collapsed: true
-->
> `card_links_2` · table in Notices
>
> One row per card, with 13 columns and about 205 thousand rows.
>
> **Belongs to** [`ebook_logs`](#ebook_logs)

- 🔑 id <!-- orbit status: "primary key" -->
  > `card_links_2.id` · `int8` · required
- 🔗 review_id <!-- orbit link: "#review_links", status: "foreign key" -->
  > `card_links_2.review_id` · `int8` · required
  >
  > Points at [`review_links`](#review_links).
- 🔢 ebook_language
  > `card_links_2.ebook_language` · `int4` · optional
- 🕒 fee_archived
  > `card_links_2.fee_archived` · `timestamp` · required
- 🕒 loan_kind
  > `card_links_2.loan_kind` · `timestamp` · required
- 🕒 review_format
  > `card_links_2.review_format` · `timestamp` · required
- 🔢 order_kind
  > `card_links_2.order_kind` · `int4` · optional
- 🔗 review_id_2 <!-- orbit link: "#review_counts", status: "foreign key" -->
  > `card_links_2.review_id_2` · `int8` · required
  >
  > Points at [`review_counts`](#review_counts).
- ☑️ loan_kind_2
  > `card_links_2.loan_kind_2` · `bool` · required
- ☑️ member_notes
  > `card_links_2.member_notes` · `bool` · required
- ☑️ report_format
  > `card_links_2.report_format` · `bool` · required
- 🔤 fee_format
  > `card_links_2.fee_format` · `varchar` · required
- 🔗 payment_id <!-- orbit link: "#payment_settings", status: "foreign key" -->
  > `card_links_2.payment_id` · `int8` · required
  >
  > Points at [`payment_settings`](#payment_settings).

### review_counts
<!-- orbit
id: "review_counts"
icon: "📗"
collapsed: true
-->
> `review_counts` · table in Notices
>
> One row per review, with 16 columns and about 486 thousand rows.
>
> **Belongs to** [`payment_versions`](#payment_versions) · [`report_versions`](#report_versions)

- 🔑 id <!-- orbit status: "primary key" -->
  > `review_counts.id` · `int8` · required
- 🔤 book_notes
  > `review_counts.book_notes` · `varchar` · optional
- 🕒 loan_public
  > `review_counts.loan_public` · `timestamp` · optional
- 🧩 donation_title
  > `review_counts.donation_title` · `jsonb` · required
- ☑️ author_count
  > `review_counts.author_count` · `bool` · required
- ☑️ copy_reviewed
  > `review_counts.copy_reviewed` · `bool` · required
- 🔗 report_id <!-- orbit link: "#report_queues", status: "foreign key" -->
  > `review_counts.report_id` · `int8` · required
  >
  > Points at [`report_queues`](#report_queues).
- ☑️ series_amount
  > `review_counts.series_amount` · `bool` · optional
- 🔢 member_status
  > `review_counts.member_status` · `int4` · optional
- 🔗 review_id <!-- orbit link: "#review_links", status: "foreign key" -->
  > `review_counts.review_id` · `int8` · required
  >
  > Points at [`review_links`](#review_links).
- ☑️ loan_source
  > `review_counts.loan_source` · `bool` · optional
- 🔤 donation_started
  > `review_counts.donation_started` · `varchar` · required
- 🔢 budget_external
  > `review_counts.budget_external` · `int4` · optional
- 🔗 loan_id <!-- orbit link: "#loan_logs", status: "foreign key" -->
  > `review_counts.loan_id` · `int8` · required
  >
  > Points at [`loan_logs`](#loan_logs).
- 🔤 author_archived
  > `review_counts.author_archived` · `varchar` · required
- 🧩 audit_reviewed
  > `review_counts.audit_reviewed` · `jsonb` · optional

### vendor_counts
<!-- orbit
id: "vendor_counts"
icon: "🧮"
collapsed: true
-->
> `vendor_counts` · table in Notices
>
> One row per vendor, with 16 columns and about 78 thousand rows.
>
> **Belongs to** [`loan_settings`](#loan_settings)

- 🔑 id <!-- orbit status: "primary key" -->
  > `vendor_counts.id` · `int8` · required
- 🔢 room_format
  > `vendor_counts.room_format` · `int4` · required
- 🔤 budget_kind
  > `vendor_counts.budget_kind` · `varchar` · required
- 🧩 report_format
  > `vendor_counts.report_format` · `jsonb` · required
- 🔗 audit_id <!-- orbit link: "#audit_links_2", status: "foreign key" -->
  > `vendor_counts.audit_id` · `int8` · required
  >
  > Points at [`audit_links_2`](#audit_links_2).
- ☑️ budget_count
  > `vendor_counts.budget_count` · `bool` · required
- ☑️ book_title
  > `vendor_counts.book_title` · `bool` · optional
- 🔢 hold_amount
  > `vendor_counts.hold_amount` · `int4` · optional
- 🕒 tag_archived
  > `vendor_counts.tag_archived` · `timestamp` · required
- 🔤 review_active
  > `vendor_counts.review_active` · `varchar` · required
- 🧩 review_count
  > `vendor_counts.review_count` · `jsonb` · required
- 🧩 repair_created
  > `vendor_counts.repair_created` · `jsonb` · required
- 🔗 series_id <!-- orbit link: "#series_counts", status: "foreign key" -->
  > `vendor_counts.series_id` · `int8` · required
  >
  > Points at [`series_counts`](#series_counts).
- 🧩 notice_active
  > `vendor_counts.notice_active` · `jsonb` · required
- 🔢 role_reviewed
  > `vendor_counts.role_reviewed` · `int4` · required
- 🔗 budgets_id <!-- orbit link: "#budgets", status: "foreign key" -->
  > `vendor_counts.budgets_id` · `int8` · required
  >
  > Points at [`budgets`](#budgets).

### staff_settings
<!-- orbit
id: "staff_settings"
icon: "🗃️"
collapsed: true
-->
> `staff_settings` · table in Notices
>
> One row per staff, with 20 columns and about 732 thousand rows.
>
> **Belongs to** [`ebook_requests`](#ebook_requests)

- 🔑 id <!-- orbit status: "primary key" -->
  > `staff_settings.id` · `int8` · required
- 🔢 tag_kind
  > `staff_settings.tag_kind` · `int4` · required
- 🧩 vendor_external
  > `staff_settings.vendor_external` · `jsonb` · required
- 🕒 report_external
  > `staff_settings.report_external` · `timestamp` · required
- 🔗 audit_id <!-- orbit link: "#audit_links_2", status: "foreign key" -->
  > `staff_settings.audit_id` · `int8` · required
  >
  > Points at [`audit_links_2`](#audit_links_2).
- ☑️ device_created
  > `staff_settings.device_created` · `bool` · optional
- 🔢 device_state
  > `staff_settings.device_state` · `int4` · required
- 🕒 card_language
  > `staff_settings.card_language` · `timestamp` · optional
- ☑️ member_title
  > `staff_settings.member_title` · `bool` · required
- 🔤 author_count
  > `staff_settings.author_count` · `varchar` · required
- ☑️ order_state
  > `staff_settings.order_state` · `bool` · required
- 🧩 payment_notes
  > `staff_settings.payment_notes` · `jsonb` · required
- 🕒 book_region
  > `staff_settings.book_region` · `timestamp` · required
- 🧩 report_reviewed
  > `staff_settings.report_reviewed` · `jsonb` · optional
- 🕒 donation_ended
  > `staff_settings.donation_ended` · `timestamp` · optional
- 🕒 repair_title
  > `staff_settings.repair_title` · `timestamp` · optional
- 🕒 branch_ended
  > `staff_settings.branch_ended` · `timestamp` · optional
- 🔤 room_count
  > `staff_settings.room_count` · `varchar` · required
- 🧩 license_position
  > `staff_settings.license_position` · `jsonb` · required
- 🔢 author_position
  > `staff_settings.author_position` · `int4` · required

### visit_queues
<!-- orbit
id: "visit_queues"
icon: "🗂️"
collapsed: true
-->
> `visit_queues` · table in Notices
>
> One row per visit, with 18 columns and about 48 thousand rows.
>
> **Belongs to** [`report_items`](#report_items) · [`shelf_counts`](#shelf_counts) · [`card_queues`](#card_queues) · [`device_settings`](#device_settings)

- 🔑 id <!-- orbit status: "primary key" -->
  > `visit_queues.id` · `int8` · required
- ☑️ event_state
  > `visit_queues.event_state` · `bool` · optional
- 🧩 tag_label
  > `visit_queues.tag_label` · `jsonb` · required
- 🧩 license_state
  > `visit_queues.license_state` · `jsonb` · required
- 🔤 report_active
  > `visit_queues.report_active` · `varchar` · optional
- 🕒 book_amount
  > `visit_queues.book_amount` · `timestamp` · optional
- 🕒 payment_kind
  > `visit_queues.payment_kind` · `timestamp` · required
- ☑️ visit_created
  > `visit_queues.visit_created` · `bool` · required
- 🕒 tag_format
  > `visit_queues.tag_format` · `timestamp` · required
- 🔤 donation_name
  > `visit_queues.donation_name` · `varchar` · required
- 🔤 visit_notes
  > `visit_queues.visit_notes` · `varchar` · required
- ☑️ order_count
  > `visit_queues.order_count` · `bool` · required
- 🧩 device_position
  > `visit_queues.device_position` · `jsonb` · optional
- 🕒 notice_code
  > `visit_queues.notice_code` · `timestamp` · required
- 🔢 device_state
  > `visit_queues.device_state` · `int4` · optional
- 🧩 report_label
  > `visit_queues.report_label` · `jsonb` · optional
- 🕒 device_count
  > `visit_queues.device_count` · `timestamp` · optional
- 🔢 repair_state
  > `visit_queues.repair_state` · `int4` · optional

### loan_logs_2
<!-- orbit
id: "loan_logs_2"
icon: "🗂️"
collapsed: true
-->
> `loan_logs_2` · table in Notices
>
> One row per loan, with 24 columns and about 9 thousand rows.
>
> **Belongs to** [`tag_settings`](#tag_settings) · [`series_links`](#series_links) · [`shelf_counts`](#shelf_counts)

- 🔑 id <!-- orbit status: "primary key" -->
  > `loan_logs_2.id` · `int8` · required
- 🔗 card_id <!-- orbit link: "#card_queues", status: "foreign key" -->
  > `loan_logs_2.card_id` · `int8` · required
  >
  > Points at [`card_queues`](#card_queues).
- ☑️ shelf_region
  > `loan_logs_2.shelf_region` · `bool` · optional
- 🕒 order_source
  > `loan_logs_2.order_source` · `timestamp` · optional
- ☑️ series_name
  > `loan_logs_2.series_name` · `bool` · required
- 🕒 order_code
  > `loan_logs_2.order_code` · `timestamp` · required
- ☑️ role_amount
  > `loan_logs_2.role_amount` · `bool` · required
- 🧩 review_public
  > `loan_logs_2.review_public` · `jsonb` · required
- 🔤 loan_kind
  > `loan_logs_2.loan_kind` · `varchar` · optional
- 🔗 card_id_2 <!-- orbit link: "#card_links", status: "foreign key" -->
  > `loan_logs_2.card_id_2` · `int8` · required
  >
  > Points at [`card_links`](#card_links).
- 🧩 visit_active
  > `loan_logs_2.visit_active` · `jsonb` · optional
- 🧩 fee_active
  > `loan_logs_2.fee_active` · `jsonb` · optional
- 🔗 card_id_3 <!-- orbit link: "#card_links", status: "foreign key" -->
  > `loan_logs_2.card_id_3` · `int8` · required
  >
  > Points at [`card_links`](#card_links).
- 🧩 vendor_language
  > `loan_logs_2.vendor_language` · `jsonb` · required
- 🧩 shelf_count
  > `loan_logs_2.shelf_count` · `jsonb` · required
- 🔗 loan_id <!-- orbit link: "#loan_counts", status: "foreign key" -->
  > `loan_logs_2.loan_id` · `int8` · required
  >
  > Points at [`loan_counts`](#loan_counts).
- 🧩 author_kind
  > `loan_logs_2.author_kind` · `jsonb` · optional
- 🧩 copy_ended
  > `loan_logs_2.copy_ended` · `jsonb` · required
- ☑️ member_title
  > `loan_logs_2.member_title` · `bool` · required
- 🧩 review_code
  > `loan_logs_2.review_code` · `jsonb` · required
- 🔗 licenses_id <!-- orbit link: "#licenses", status: "foreign key" -->
  > `loan_logs_2.licenses_id` · `int8` · required
  >
  > Points at [`licenses`](#licenses).
- 🕒 card_format
  > `loan_logs_2.card_format` · `timestamp` · optional
- 🔤 room_position
  > `loan_logs_2.room_position` · `varchar` · optional
- ☑️ visit_amount
  > `loan_logs_2.visit_amount` · `bool` · required

### donations
<!-- orbit
id: "donations"
icon: "🗂️"
collapsed: true
-->
> `donations` · table in Notices
>
> One row per donations, with 19 columns and about 377 thousand rows.
>
> **Belongs to** [`budget_settings`](#budget_settings)

- 🔑 id <!-- orbit status: "primary key" -->
  > `donations.id` · `int8` · required
- 🔤 series_source
  > `donations.series_source` · `varchar` · optional
- ☑️ ebook_name
  > `donations.ebook_name` · `bool` · required
- 🔗 repair_id <!-- orbit link: "#repair_requests", status: "foreign key" -->
  > `donations.repair_id` · `int8` · required
  >
  > Points at [`repair_requests`](#repair_requests).
- 🔤 member_name
  > `donations.member_name` · `varchar` · optional
- 🔤 audit_total
  > `donations.audit_total` · `varchar` · required
- ☑️ staff_name
  > `donations.staff_name` · `bool` · optional
- 🔤 hold_name
  > `donations.hold_name` · `varchar` · required
- 🔢 staff_external
  > `donations.staff_external` · `int4` · required
- 🔢 report_started
  > `donations.report_started` · `int4` · optional
- 🕒 ebook_amount
  > `donations.ebook_amount` · `timestamp` · optional
- 🔤 ebook_active
  > `donations.ebook_active` · `varchar` · optional
- 🕒 shelf_name
  > `donations.shelf_name` · `timestamp` · required
- 🔗 vendor_id <!-- orbit link: "#vendor_history", status: "foreign key" -->
  > `donations.vendor_id` · `int8` · required
  >
  > Points at [`vendor_history`](#vendor_history).
- 🕒 review_updated
  > `donations.review_updated` · `timestamp` · optional
- ☑️ role_state
  > `donations.role_state` · `bool` · optional
- 🔗 staff_id <!-- orbit link: "#staff_counts", status: "foreign key" -->
  > `donations.staff_id` · `int8` · required
  >
  > Points at [`staff_counts`](#staff_counts).
- 🕒 vendor_label
  > `donations.vendor_label` · `timestamp` · optional
- 🔢 notice_position
  > `donations.notice_position` · `int4` · optional

### review_logs
<!-- orbit
id: "review_logs"
icon: "🧷"
collapsed: true
-->
> `review_logs` · table in Notices
>
> One row per review, with 7 columns and about 143 thousand rows.
>
> **Belongs to** [`copy_versions`](#copy_versions) · [`review_counts`](#review_counts) · [`donation_history`](#donation_history) · [`tags`](#tags)

- 🔑 id <!-- orbit status: "primary key" -->
  > `review_logs.id` · `int8` · required
- 🧩 license_position
  > `review_logs.license_position` · `jsonb` · optional
- 🧩 staff_count
  > `review_logs.staff_count` · `jsonb` · optional
- ☑️ review_total
  > `review_logs.review_total` · `bool` · optional
- 🔗 staff_id <!-- orbit link: "#staff_items", status: "foreign key" -->
  > `review_logs.staff_id` · `int8` · required
  >
  > Points at [`staff_items`](#staff_items).
- 🔗 report_id <!-- orbit link: "#report_items", status: "foreign key" -->
  > `review_logs.report_id` · `int8` · required
  >
  > Points at [`report_items`](#report_items).
- 🔤 copy_source
  > `review_logs.copy_source` · `varchar` · required

### license_settings
<!-- orbit
id: "license_settings"
icon: "🗂️"
collapsed: true
-->
> `license_settings` · table in Notices
>
> One row per license, with 10 columns and about 325 thousand rows.
>
> **Belongs to** [`ebook_queues`](#ebook_queues)

- 🔑 id <!-- orbit status: "primary key" -->
  > `license_settings.id` · `int8` · required
- 🕒 notice_created
  > `license_settings.notice_created` · `timestamp` · optional
- ☑️ author_created
  > `license_settings.author_created` · `bool` · optional
- 🔗 vendor_id <!-- orbit link: "#vendor_items", status: "foreign key" -->
  > `license_settings.vendor_id` · `int8` · required
  >
  > Points at [`vendor_items`](#vendor_items).
- 🕒 loan_source
  > `license_settings.loan_source` · `timestamp` · required
- 🔗 seriess_id <!-- orbit link: "#seriess", status: "foreign key" -->
  > `license_settings.seriess_id` · `int8` · required
  >
  > Points at [`seriess`](#seriess).
- 🕒 notice_updated
  > `license_settings.notice_updated` · `timestamp` · required
- 🔤 repair_created
  > `license_settings.repair_created` · `varchar` · required
- 🕒 order_kind
  > `license_settings.order_kind` · `timestamp` · optional
- 🔗 license_id <!-- orbit link: "#license_logs_2", status: "foreign key" -->
  > `license_settings.license_id` · `int8` · required
  >
  > Points at [`license_logs_2`](#license_logs_2).

## 🌐 Digital Lending
<!-- orbit
id: "area-digital-lending"
color: "#059669"
-->
> 10 tables. Largest: [`budgets`](#budgets), [`licenses`](#licenses), [`audit_links_4`](#audit_links_4).
>
> **Linked to** [`rooms`](#rooms) · [`license_logs`](#license_logs) · [`budget_settings`](#budget_settings)

### budgets
<!-- orbit
id: "budgets"
icon: "🧮"
collapsed: true
-->
> `budgets` · table in Digital Lending
>
> One row per budgets, with 7 columns and about 227 thousand rows.
>
> **Belongs to** [`card_links_2`](#card_links_2) · [`ebook_logs`](#ebook_logs) · [`series_counts`](#series_counts)

- 🔑 id <!-- orbit status: "primary key" -->
  > `budgets.id` · `int8` · required
- ☑️ notice_updated
  > `budgets.notice_updated` · `bool` · required
- 🕒 author_ended
  > `budgets.author_ended` · `timestamp` · optional
- 🕒 loan_language
  > `budgets.loan_language` · `timestamp` · optional
- 🔗 vendor_id <!-- orbit link: "#vendor_counts", status: "foreign key" -->
  > `budgets.vendor_id` · `int8` · required
  >
  > Points at [`vendor_counts`](#vendor_counts).
- ☑️ visit_source
  > `budgets.visit_source` · `bool` · required
- 🔢 staff_count
  > `budgets.staff_count` · `int4` · required

### licenses
<!-- orbit
id: "licenses"
icon: "🗃️"
collapsed: true
-->
> `licenses` · table in Digital Lending
>
> One row per licenses, with 19 columns and about 709 thousand rows.
>
> **Belongs to** [`visits`](#visits) · [`hold_links`](#hold_links)

- 🔑 id <!-- orbit status: "primary key" -->
  > `licenses.id` · `int8` · required
- ☑️ order_state
  > `licenses.order_state` · `bool` · required
- 🔢 review_title
  > `licenses.review_title` · `int4` · required
- 🔢 hold_kind
  > `licenses.hold_kind` · `int4` · optional
- ☑️ report_code
  > `licenses.report_code` · `bool` · required
- 🔗 events_id <!-- orbit link: "#events", status: "foreign key" -->
  > `licenses.events_id` · `int8` · required
  >
  > Points at [`events`](#events).
- 🕒 repair_status
  > `licenses.repair_status` · `timestamp` · optional
- ☑️ series_started
  > `licenses.series_started` · `bool` · optional
- 🧩 copy_priority
  > `licenses.copy_priority` · `jsonb` · required
- 🔗 loan_id <!-- orbit link: "#loan_logs", status: "foreign key" -->
  > `licenses.loan_id` · `int8` · required
  >
  > Points at [`loan_logs`](#loan_logs).
- 🔤 budget_language
  > `licenses.budget_language` · `varchar` · required
- ☑️ notice_archived
  > `licenses.notice_archived` · `bool` · optional
- 🕒 budget_archived
  > `licenses.budget_archived` · `timestamp` · required
- 🔗 budgets_id <!-- orbit link: "#budgets", status: "foreign key" -->
  > `licenses.budgets_id` · `int8` · required
  >
  > Points at [`budgets`](#budgets).
- 🕒 tag_kind
  > `licenses.tag_kind` · `timestamp` · optional
- 🔤 order_external
  > `licenses.order_external` · `varchar` · required
- 🧩 role_started
  > `licenses.role_started` · `jsonb` · optional
- 🔤 event_state
  > `licenses.event_state` · `varchar` · optional
- 🔢 license_notes
  > `licenses.license_notes` · `int4` · required

### audit_links_4
<!-- orbit
id: "audit_links_4"
icon: "🧷"
collapsed: true
-->
> `audit_links_4` · table in Digital Lending
>
> One row per audit, with 24 columns and about 461 thousand rows.
>
> **Belongs to** [`member_counts_2`](#member_counts_2) · [`report_queues`](#report_queues)

- 🔑 id <!-- orbit status: "primary key" -->
  > `audit_links_4.id` · `int8` · required
- 🔢 copy_public
  > `audit_links_4.copy_public` · `int4` · optional
- 🔢 hold_started
  > `audit_links_4.hold_started` · `int4` · optional
- 🕒 role_title
  > `audit_links_4.role_title` · `timestamp` · required
- 🧩 series_code
  > `audit_links_4.series_code` · `jsonb` · optional
- ☑️ role_kind
  > `audit_links_4.role_kind` · `bool` · optional
- 🧩 visit_updated
  > `audit_links_4.visit_updated` · `jsonb` · required
- 🔗 series_id <!-- orbit link: "#series_requests", status: "foreign key" -->
  > `audit_links_4.series_id` · `int8` · required
  >
  > Points at [`series_requests`](#series_requests).
- 🧩 tag_code
  > `audit_links_4.tag_code` · `jsonb` · required
- 🧩 donation_updated
  > `audit_links_4.donation_updated` · `jsonb` · optional
- 🧩 vendor_external
  > `audit_links_4.vendor_external` · `jsonb` · required
- 🧩 event_archived
  > `audit_links_4.event_archived` · `jsonb` · optional
- 🔤 notice_code
  > `audit_links_4.notice_code` · `varchar` · required
- 🔤 budget_priority
  > `audit_links_4.budget_priority` · `varchar` · optional
- ☑️ staff_state
  > `audit_links_4.staff_state` · `bool` · required
- 🔢 donation_code
  > `audit_links_4.donation_code` · `int4` · optional
- ☑️ notice_kind
  > `audit_links_4.notice_kind` · `bool` · required
- 🔗 room_id <!-- orbit link: "#room_counts_2", status: "foreign key" -->
  > `audit_links_4.room_id` · `int8` · required
  >
  > Points at [`room_counts_2`](#room_counts_2).
- 🔤 shelf_active
  > `audit_links_4.shelf_active` · `varchar` · required
- ☑️ order_language
  > `audit_links_4.order_language` · `bool` · required
- 🕒 ebook_ended
  > `audit_links_4.ebook_ended` · `timestamp` · optional
- 🔢 staff_amount
  > `audit_links_4.staff_amount` · `int4` · required
- 🕒 book_language
  > `audit_links_4.book_language` · `timestamp` · optional
- 🔗 branch_id <!-- orbit link: "#branch_links", status: "foreign key" -->
  > `audit_links_4.branch_id` · `int8` · required
  >
  > Points at [`branch_links`](#branch_links).

### room_counts_2
<!-- orbit
id: "room_counts_2"
icon: "📇"
collapsed: true
-->
> `room_counts_2` · table in Digital Lending
>
> One row per room, with 9 columns and about 509 thousand rows.
>
> **Belongs to** [`report_logs`](#report_logs)

- 🔑 id <!-- orbit status: "primary key" -->
  > `room_counts_2.id` · `int8` · required
- 🔗 author_id <!-- orbit link: "#author_links", status: "foreign key" -->
  > `room_counts_2.author_id` · `int8` · required
  >
  > Points at [`author_links`](#author_links).
- 🕒 visit_status
  > `room_counts_2.visit_status` · `timestamp` · optional
- 🕒 branch_format
  > `room_counts_2.branch_format` · `timestamp` · optional
- ☑️ series_ended
  > `room_counts_2.series_ended` · `bool` · optional
- 🔗 loan_id <!-- orbit link: "#loan_settings", status: "foreign key" -->
  > `room_counts_2.loan_id` · `int8` · required
  >
  > Points at [`loan_settings`](#loan_settings).
- 🕒 review_public
  > `room_counts_2.review_public` · `timestamp` · required
- 🔗 copy_id <!-- orbit link: "#copy_items", status: "foreign key" -->
  > `room_counts_2.copy_id` · `int8` · required
  >
  > Points at [`copy_items`](#copy_items).
- 🔢 device_format
  > `room_counts_2.device_format` · `int4` · optional

### notice_counts_2
<!-- orbit
id: "notice_counts_2"
icon: "🧷"
collapsed: true
-->
> `notice_counts_2` · table in Digital Lending
>
> One row per notice, with 15 columns and about 588 thousand rows.
>
> **Belongs to** [`tags`](#tags) · [`payment_requests`](#payment_requests)

- 🔑 id <!-- orbit status: "primary key" -->
  > `notice_counts_2.id` · `int8` · required
- 🔢 event_priority
  > `notice_counts_2.event_priority` · `int4` · required
- 🕒 staff_status
  > `notice_counts_2.staff_status` · `timestamp` · optional
- 🔗 donation_id <!-- orbit link: "#donation_history", status: "foreign key" -->
  > `notice_counts_2.donation_id` · `int8` · required
  >
  > Points at [`donation_history`](#donation_history).
- ☑️ repair_created
  > `notice_counts_2.repair_created` · `bool` · required
- 🔢 order_label
  > `notice_counts_2.order_label` · `int4` · required
- 🔤 author_title
  > `notice_counts_2.author_title` · `varchar` · optional
- 🕒 room_format
  > `notice_counts_2.room_format` · `timestamp` · optional
- 🧩 review_status
  > `notice_counts_2.review_status` · `jsonb` · optional
- 🔗 review_id <!-- orbit link: "#review_links", status: "foreign key" -->
  > `notice_counts_2.review_id` · `int8` · required
  >
  > Points at [`review_links`](#review_links).
- ☑️ license_external
  > `notice_counts_2.license_external` · `bool` · optional
- ☑️ report_region
  > `notice_counts_2.report_region` · `bool` · optional
- 🔢 hold_code
  > `notice_counts_2.hold_code` · `int4` · required
- 🔤 vendor_title
  > `notice_counts_2.vendor_title` · `varchar` · optional
- 🔗 report_id <!-- orbit link: "#report_versions", status: "foreign key" -->
  > `notice_counts_2.report_id` · `int8` · required
  >
  > Points at [`report_versions`](#report_versions).

### room_settings
<!-- orbit
id: "room_settings"
icon: "🧮"
collapsed: true
-->
> `room_settings` · table in Digital Lending
>
> One row per room, with 9 columns and about 519 thousand rows.
>
> **Belongs to** [`report_logs`](#report_logs) · [`author_links`](#author_links) · [`author_history`](#author_history) · [`holds_2`](#holds_2)

- 🔑 id <!-- orbit status: "primary key" -->
  > `room_settings.id` · `int8` · required
- 🔢 author_language
  > `room_settings.author_language` · `int4` · required
- 🔤 book_total
  > `room_settings.book_total` · `varchar` · optional
- 🧩 license_position
  > `room_settings.license_position` · `jsonb` · required
- 🔢 repair_source
  > `room_settings.repair_source` · `int4` · required
- 🕒 hold_priority
  > `room_settings.hold_priority` · `timestamp` · required
- 🔤 staff_region
  > `room_settings.staff_region` · `varchar` · optional
- 🔢 order_position
  > `room_settings.order_position` · `int4` · required
- 🔤 ebook_notes
  > `room_settings.ebook_notes` · `varchar` · optional

### room_logs
<!-- orbit
id: "room_logs"
icon: "🧷"
collapsed: true
-->
> `room_logs` · table in Digital Lending
>
> One row per room, with 20 columns and about 679 thousand rows.
>
> **Belongs to** [`shelf_queues`](#shelf_queues) · [`audit_links_2`](#audit_links_2) · [`event_queues`](#event_queues)

- 🔑 id <!-- orbit status: "primary key" -->
  > `room_logs.id` · `int8` · required
- 🔢 repair_source
  > `room_logs.repair_source` · `int4` · optional
- 🔗 member_id <!-- orbit link: "#member_history", status: "foreign key" -->
  > `room_logs.member_id` · `int8` · required
  >
  > Points at [`member_history`](#member_history).
- 🔢 audit_archived
  > `room_logs.audit_archived` · `int4` · optional
- 🔗 copy_id <!-- orbit link: "#copy_history", status: "foreign key" -->
  > `room_logs.copy_id` · `int8` · required
  >
  > Points at [`copy_history`](#copy_history).
- ☑️ author_label
  > `room_logs.author_label` · `bool` · optional
- 🔗 review_id <!-- orbit link: "#review_logs", status: "foreign key" -->
  > `room_logs.review_id` · `int8` · required
  >
  > Points at [`review_logs`](#review_logs).
- ☑️ shelf_label
  > `room_logs.shelf_label` · `bool` · optional
- 🕒 budget_reviewed
  > `room_logs.budget_reviewed` · `timestamp` · required
- 🔢 repair_external
  > `room_logs.repair_external` · `int4` · required
- 🕒 card_code
  > `room_logs.card_code` · `timestamp` · optional
- 🔤 book_amount
  > `room_logs.book_amount` · `varchar` · optional
- ☑️ tag_public
  > `room_logs.tag_public` · `bool` · optional
- 🕒 report_total
  > `room_logs.report_total` · `timestamp` · required
- 🧩 payment_status
  > `room_logs.payment_status` · `jsonb` · required
- 🔗 room_id <!-- orbit link: "#room_counts", status: "foreign key" -->
  > `room_logs.room_id` · `int8` · required
  >
  > Points at [`room_counts`](#room_counts).
- 🔤 order_started
  > `room_logs.order_started` · `varchar` · optional
- 🔤 donation_updated
  > `room_logs.donation_updated` · `varchar` · required
- 🔢 notice_ended
  > `room_logs.notice_ended` · `int4` · optional
- 🔗 visit_id <!-- orbit link: "#visit_items", status: "foreign key" -->
  > `room_logs.visit_id` · `int8` · required
  >
  > Points at [`visit_items`](#visit_items).

### seriess
<!-- orbit
id: "seriess"
icon: "🧮"
collapsed: true
-->
> `seriess` · table in Digital Lending
>
> One row per seriess, with 11 columns and about 228 thousand rows.
>
> **Belongs to** [`author_history`](#author_history) · [`role_counts`](#role_counts) · [`notice_counts_2`](#notice_counts_2)

- 🔑 id <!-- orbit status: "primary key" -->
  > `seriess.id` · `int8` · required
- 🔢 notice_kind
  > `seriess.notice_kind` · `int4` · required
- 🔤 visit_state
  > `seriess.visit_state` · `varchar` · required
- ☑️ review_amount
  > `seriess.review_amount` · `bool` · optional
- ☑️ ebook_started
  > `seriess.ebook_started` · `bool` · optional
- 🕒 branch_source
  > `seriess.branch_source` · `timestamp` · optional
- 🕒 author_archived
  > `seriess.author_archived` · `timestamp` · optional
- 🔢 branch_state
  > `seriess.branch_state` · `int4` · optional
- 🧩 room_position
  > `seriess.room_position` · `jsonb` · optional
- 🔤 series_title
  > `seriess.series_title` · `varchar` · optional
- ☑️ loan_external
  > `seriess.loan_external` · `bool` · required

### visit_links
<!-- orbit
id: "visit_links"
icon: "🧮"
collapsed: true
-->
> `visit_links` · table in Digital Lending
>
> One row per visit, with 19 columns and about 376 thousand rows.
>
> **Belongs to** [`review_logs`](#review_logs) · [`audit_links_2`](#audit_links_2) · [`card_logs`](#card_logs) · [`review_versions`](#review_versions)

- 🔑 id <!-- orbit status: "primary key" -->
  > `visit_links.id` · `int8` · required
- 🔢 visit_position
  > `visit_links.visit_position` · `int4` · required
- 🔤 device_notes
  > `visit_links.device_notes` · `varchar` · optional
- 🧩 notice_label
  > `visit_links.notice_label` · `jsonb` · optional
- 🔗 audit_id <!-- orbit link: "#audit_links_3", status: "foreign key" -->
  > `visit_links.audit_id` · `int8` · required
  >
  > Points at [`audit_links_3`](#audit_links_3).
- 🔢 role_name
  > `visit_links.role_name` · `int4` · optional
- 🕒 card_created
  > `visit_links.card_created` · `timestamp` · optional
- 🧩 hold_kind
  > `visit_links.hold_kind` · `jsonb` · optional
- 🔗 loan_id <!-- orbit link: "#loan_logs_2", status: "foreign key" -->
  > `visit_links.loan_id` · `int8` · required
  >
  > Points at [`loan_logs_2`](#loan_logs_2).
- 🧩 shelf_format
  > `visit_links.shelf_format` · `jsonb` · required
- ☑️ role_source
  > `visit_links.role_source` · `bool` · optional
- 🧩 repair_code
  > `visit_links.repair_code` · `jsonb` · required
- 🔤 report_ended
  > `visit_links.report_ended` · `varchar` · optional
- 🔤 card_format
  > `visit_links.card_format` · `varchar` · optional
- ☑️ event_name
  > `visit_links.event_name` · `bool` · optional
- 🕒 room_total
  > `visit_links.room_total` · `timestamp` · required
- 🔤 event_priority
  > `visit_links.event_priority` · `varchar` · required
- ☑️ license_archived
  > `visit_links.license_archived` · `bool` · required
- 🔤 budget_source
  > `visit_links.budget_source` · `varchar` · optional

### book_items
<!-- orbit
id: "book_items"
icon: "📇"
collapsed: true
-->
> `book_items` · table in Digital Lending
>
> One row per book, with 8 columns and about 399 thousand rows.
>
> **Belongs to** [`author_history`](#author_history) · [`donation_history`](#donation_history)

- 🔑 id <!-- orbit status: "primary key" -->
  > `book_items.id` · `int8` · required
- 🕒 staff_active
  > `book_items.staff_active` · `timestamp` · optional
- 🔗 licenses_id <!-- orbit link: "#licenses", status: "foreign key" -->
  > `book_items.licenses_id` · `int8` · required
  >
  > Points at [`licenses`](#licenses).
- ☑️ fee_ended
  > `book_items.fee_ended` · `bool` · required
- 🧩 vendor_name
  > `book_items.vendor_name` · `jsonb` · optional
- 🔤 donation_count
  > `book_items.donation_count` · `varchar` · required
- 🔗 tag_id <!-- orbit link: "#tag_settings", status: "foreign key" -->
  > `book_items.tag_id` · `int8` · required
  >
  > Points at [`tag_settings`](#tag_settings).
- 🧩 audit_status
  > `book_items.audit_status` · `jsonb` · required

## 🧰 Maintenance
<!-- orbit
id: "area-maintenance"
color: "#B45309"
-->
> 10 tables. Largest: [`loan_history`](#loan_history), [`book_settings`](#book_settings), [`copy_history`](#copy_history).
>
> **Linked to** [`vendor_counts`](#vendor_counts) · [`vendor_history`](#vendor_history) · [`role_counts`](#role_counts)

### loan_history
<!-- orbit
id: "loan_history"
icon: "🧮"
collapsed: true
-->
> `loan_history` · table in Maintenance
>
> One row per loan, with 11 columns and about 728 thousand rows.
>
> **Belongs to** [`author_links_2`](#author_links_2)

- 🔑 id <!-- orbit status: "primary key" -->
  > `loan_history.id` · `int8` · required
- 🔗 copy_id <!-- orbit link: "#copy_versions", status: "foreign key" -->
  > `loan_history.copy_id` · `int8` · required
  >
  > Points at [`copy_versions`](#copy_versions).
- 🧩 member_region
  > `loan_history.member_region` · `jsonb` · optional
- 🕒 budget_title
  > `loan_history.budget_title` · `timestamp` · required
- ☑️ report_updated
  > `loan_history.report_updated` · `bool` · optional
- 🔤 author_position
  > `loan_history.author_position` · `varchar` · required
- 🔢 loan_position
  > `loan_history.loan_position` · `int4` · required
- 🔢 shelf_created
  > `loan_history.shelf_created` · `int4` · optional
- 🔤 notice_state
  > `loan_history.notice_state` · `varchar` · optional
- 🔗 staff_id <!-- orbit link: "#staff_settings", status: "foreign key" -->
  > `loan_history.staff_id` · `int8` · required
  >
  > Points at [`staff_settings`](#staff_settings).
- ☑️ audit_kind
  > `loan_history.audit_kind` · `bool` · optional

### book_settings
<!-- orbit
id: "book_settings"
icon: "🗃️"
collapsed: true
-->
> `book_settings` · table in Maintenance
>
> One row per book, with 12 columns and about 113 thousand rows.
>
> **Belongs to** [`audit_links_3`](#audit_links_3) · [`review_queues`](#review_queues)

- 🔑 id <!-- orbit status: "primary key" -->
  > `book_settings.id` · `int8` · required
- 🔢 book_source
  > `book_settings.book_source` · `int4` · optional
- ☑️ report_source
  > `book_settings.report_source` · `bool` · optional
- 🔗 ebook_id <!-- orbit link: "#ebook_logs", status: "foreign key" -->
  > `book_settings.ebook_id` · `int8` · required
  >
  > Points at [`ebook_logs`](#ebook_logs).
- 🔗 budget_id <!-- orbit link: "#budget_queues", status: "foreign key" -->
  > `book_settings.budget_id` · `int8` · required
  >
  > Points at [`budget_queues`](#budget_queues).
- 🔤 series_format
  > `book_settings.series_format` · `varchar` · required
- 🧩 shelf_label
  > `book_settings.shelf_label` · `jsonb` · required
- 🕒 copy_region
  > `book_settings.copy_region` · `timestamp` · required
- 🧩 repair_ended
  > `book_settings.repair_ended` · `jsonb` · required
- 🔤 tag_source
  > `book_settings.tag_source` · `varchar` · optional
- 🔤 ebook_priority
  > `book_settings.ebook_priority` · `varchar` · required
- 🔢 visit_code
  > `book_settings.visit_code` · `int4` · required

### copy_history
<!-- orbit
id: "copy_history"
icon: "📇"
collapsed: true
-->
> `copy_history` · table in Maintenance
>
> One row per copy, with 20 columns and about 547 thousand rows.
>
> **Belongs to** [`staff_counts`](#staff_counts) · [`series_counts_2`](#series_counts_2) · [`tag_settings`](#tag_settings) · [`budget_settings_2`](#budget_settings_2)

- 🔑 id <!-- orbit status: "primary key" -->
  > `copy_history.id` · `int8` · required
- 🔢 hold_total
  > `copy_history.hold_total` · `int4` · optional
- ☑️ staff_kind
  > `copy_history.staff_kind` · `bool` · required
- 🔗 device_id <!-- orbit link: "#device_settings", status: "foreign key" -->
  > `copy_history.device_id` · `int8` · required
  >
  > Points at [`device_settings`](#device_settings).
- 🔢 audit_external
  > `copy_history.audit_external` · `int4` · required
- 🧩 room_status
  > `copy_history.room_status` · `jsonb` · optional
- 🕒 audit_code
  > `copy_history.audit_code` · `timestamp` · required
- ☑️ staff_total
  > `copy_history.staff_total` · `bool` · required
- 🧩 budget_language
  > `copy_history.budget_language` · `jsonb` · optional
- 🔤 vendor_kind
  > `copy_history.vendor_kind` · `varchar` · required
- 🔢 role_ended
  > `copy_history.role_ended` · `int4` · optional
- 🔤 staff_archived
  > `copy_history.staff_archived` · `varchar` · optional
- 🔗 hold_id <!-- orbit link: "#hold_history", status: "foreign key" -->
  > `copy_history.hold_id` · `int8` · required
  >
  > Points at [`hold_history`](#hold_history).
- 🔗 series_id <!-- orbit link: "#series_requests", status: "foreign key" -->
  > `copy_history.series_id` · `int8` · required
  >
  > Points at [`series_requests`](#series_requests).
- 🔗 series_id_2 <!-- orbit link: "#series_history", status: "foreign key" -->
  > `copy_history.series_id_2` · `int8` · required
  >
  > Points at [`series_history`](#series_history).
- 🔗 license_id <!-- orbit link: "#license_logs_2", status: "foreign key" -->
  > `copy_history.license_id` · `int8` · required
  >
  > Points at [`license_logs_2`](#license_logs_2).
- 🔢 staff_archived_2
  > `copy_history.staff_archived_2` · `int4` · optional
- 🧩 device_language
  > `copy_history.device_language` · `jsonb` · optional
- 🔗 donation_id <!-- orbit link: "#donation_requests", status: "foreign key" -->
  > `copy_history.donation_id` · `int8` · required
  >
  > Points at [`donation_requests`](#donation_requests).
- 🕒 device_format
  > `copy_history.device_format` · `timestamp` · optional

### report_counts
<!-- orbit
id: "report_counts"
icon: "📇"
collapsed: true
-->
> `report_counts` · table in Maintenance
>
> One row per report, with 24 columns and about 542 thousand rows.
>
> **Belongs to** [`order_links`](#order_links) · [`loan_history`](#loan_history) · [`review_items`](#review_items) · [`notice_counts_2`](#notice_counts_2)

- 🔑 id <!-- orbit status: "primary key" -->
  > `report_counts.id` · `int8` · required
- 🕒 shelf_updated
  > `report_counts.shelf_updated` · `timestamp` · required
- 🔤 order_started
  > `report_counts.order_started` · `varchar` · optional
- 🔢 notice_status
  > `report_counts.notice_status` · `int4` · required
- ☑️ budget_language
  > `report_counts.budget_language` · `bool` · required
- 🔤 donation_kind
  > `report_counts.donation_kind` · `varchar` · optional
- 🔗 member_id <!-- orbit link: "#member_versions", status: "foreign key" -->
  > `report_counts.member_id` · `int8` · required
  >
  > Points at [`member_versions`](#member_versions).
- 🔗 donation_id <!-- orbit link: "#donation_queues", status: "foreign key" -->
  > `report_counts.donation_id` · `int8` · required
  >
  > Points at [`donation_queues`](#donation_queues).
- 🔗 room_id <!-- orbit link: "#room_counts_2", status: "foreign key" -->
  > `report_counts.room_id` · `int8` · required
  >
  > Points at [`room_counts_2`](#room_counts_2).
- ☑️ book_code
  > `report_counts.book_code` · `bool` · required
- 🕒 ebook_started
  > `report_counts.ebook_started` · `timestamp` · required
- ☑️ visit_ended
  > `report_counts.visit_ended` · `bool` · required
- 🕒 room_code
  > `report_counts.room_code` · `timestamp` · required
- 🔗 loan_id <!-- orbit link: "#loan_settings", status: "foreign key" -->
  > `report_counts.loan_id` · `int8` · required
  >
  > Points at [`loan_settings`](#loan_settings).
- 🕒 repair_position
  > `report_counts.repair_position` · `timestamp` · optional
- 🕒 staff_total
  > `report_counts.staff_total` · `timestamp` · optional
- 🔤 fee_format
  > `report_counts.fee_format` · `varchar` · required
- 🔤 repair_title
  > `report_counts.repair_title` · `varchar` · required
- 🕒 ebook_kind
  > `report_counts.ebook_kind` · `timestamp` · required
- ☑️ payment_state
  > `report_counts.payment_state` · `bool` · optional
- ☑️ shelf_public
  > `report_counts.shelf_public` · `bool` · required
- 🔤 fee_priority
  > `report_counts.fee_priority` · `varchar` · required
- 🔗 loan_id_2 <!-- orbit link: "#loan_logs_2", status: "foreign key" -->
  > `report_counts.loan_id_2` · `int8` · required
  >
  > Points at [`loan_logs_2`](#loan_logs_2).
- 🔢 staff_kind
  > `report_counts.staff_kind` · `int4` · optional

### event_queues
<!-- orbit
id: "event_queues"
icon: "🧮"
collapsed: true
-->
> `event_queues` · table in Maintenance
>
> One row per event, with 21 columns and about 403 thousand rows.
>
> **Belongs to** [`loan_counts`](#loan_counts) · [`license_logs_2`](#license_logs_2) · [`notice_counts`](#notice_counts) · [`donation_links`](#donation_links)

- 🔑 id <!-- orbit status: "primary key" -->
  > `event_queues.id` · `int8` · required
- 🔢 role_count
  > `event_queues.role_count` · `int4` · required
- 🔢 donation_format
  > `event_queues.donation_format` · `int4` · optional
- 🕒 room_position
  > `event_queues.room_position` · `timestamp` · required
- ☑️ review_position
  > `event_queues.review_position` · `bool` · required
- 🧩 budget_count
  > `event_queues.budget_count` · `jsonb` · required
- 🕒 ebook_updated
  > `event_queues.ebook_updated` · `timestamp` · optional
- 🔢 notice_label
  > `event_queues.notice_label` · `int4` · required
- 🕒 review_position_2
  > `event_queues.review_position_2` · `timestamp` · optional
- 🔢 repair_active
  > `event_queues.repair_active` · `int4` · required
- 🔢 role_code
  > `event_queues.role_code` · `int4` · optional
- 🔗 hold_id <!-- orbit link: "#hold_links_2", status: "foreign key" -->
  > `event_queues.hold_id` · `int8` · required
  >
  > Points at [`hold_links_2`](#hold_links_2).
- 🔗 audit_id <!-- orbit link: "#audit_links", status: "foreign key" -->
  > `event_queues.audit_id` · `int8` · required
  >
  > Points at [`audit_links`](#audit_links).
- 🔤 room_archived
  > `event_queues.room_archived` · `varchar` · required
- ☑️ hold_state
  > `event_queues.hold_state` · `bool` · optional
- 🔤 review_name
  > `event_queues.review_name` · `varchar` · optional
- 🔢 visit_source
  > `event_queues.visit_source` · `int4` · optional
- 🔤 ebook_started
  > `event_queues.ebook_started` · `varchar` · optional
- 🔢 donation_count
  > `event_queues.donation_count` · `int4` · required
- ☑️ event_title
  > `event_queues.event_title` · `bool` · required
- 🔢 audit_region
  > `event_queues.audit_region` · `int4` · optional

### budget_settings_2
<!-- orbit
id: "budget_settings_2"
icon: "🧷"
collapsed: true
-->
> `budget_settings_2` · table in Maintenance
>
> One row per budget, with 18 columns and about 799 thousand rows.
>
> **Belongs to** [`license_versions`](#license_versions) · [`book_links`](#book_links)

- 🔑 id <!-- orbit status: "primary key" -->
  > `budget_settings_2.id` · `int8` · required
- ☑️ author_total
  > `budget_settings_2.author_total` · `bool` · optional
- 🔗 order_id <!-- orbit link: "#order_links", status: "foreign key" -->
  > `budget_settings_2.order_id` · `int8` · required
  >
  > Points at [`order_links`](#order_links).
- 🧩 vendor_source
  > `budget_settings_2.vendor_source` · `jsonb` · required
- 🕒 device_public
  > `budget_settings_2.device_public` · `timestamp` · optional
- 🔤 series_priority
  > `budget_settings_2.series_priority` · `varchar` · required
- 🔢 author_priority
  > `budget_settings_2.author_priority` · `int4` · required
- 🕒 budget_amount
  > `budget_settings_2.budget_amount` · `timestamp` · required
- 🕒 visit_label
  > `budget_settings_2.visit_label` · `timestamp` · required
- 🔗 visit_id <!-- orbit link: "#visit_items", status: "foreign key" -->
  > `budget_settings_2.visit_id` · `int8` · required
  >
  > Points at [`visit_items`](#visit_items).
- 🕒 audit_region
  > `budget_settings_2.audit_region` · `timestamp` · required
- 🔤 room_total
  > `budget_settings_2.room_total` · `varchar` · optional
- 🔗 vendor_id <!-- orbit link: "#vendor_settings", status: "foreign key" -->
  > `budget_settings_2.vendor_id` · `int8` · required
  >
  > Points at [`vendor_settings`](#vendor_settings).
- 🧩 notice_position
  > `budget_settings_2.notice_position` · `jsonb` · required
- 🔢 report_code
  > `budget_settings_2.report_code` · `int4` · required
- 🧩 repair_language
  > `budget_settings_2.repair_language` · `jsonb` · optional
- 🔢 series_created
  > `budget_settings_2.series_created` · `int4` · required
- 🕒 loan_source
  > `budget_settings_2.loan_source` · `timestamp` · required

### series_counts_2
<!-- orbit
id: "series_counts_2"
icon: "📇"
collapsed: true
-->
> `series_counts_2` · table in Maintenance
>
> One row per series, with 11 columns and about 263 thousand rows.
>
> **Belongs to** [`loan_counts`](#loan_counts) · [`branchs`](#branchs) · [`visit_items`](#visit_items) · [`holds_2`](#holds_2)

- 🔑 id <!-- orbit status: "primary key" -->
  > `series_counts_2.id` · `int8` · required
- 🔗 member_id <!-- orbit link: "#member_counts_2", status: "foreign key" -->
  > `series_counts_2.member_id` · `int8` · required
  >
  > Points at [`member_counts_2`](#member_counts_2).
- 🔤 hold_archived
  > `series_counts_2.hold_archived` · `varchar` · required
- ☑️ notice_position
  > `series_counts_2.notice_position` · `bool` · required
- 🔤 fee_archived
  > `series_counts_2.fee_archived` · `varchar` · optional
- 🔤 card_language
  > `series_counts_2.card_language` · `varchar` · optional
- ☑️ series_title
  > `series_counts_2.series_title` · `bool` · optional
- 🔗 tag_id <!-- orbit link: "#tag_counts", status: "foreign key" -->
  > `series_counts_2.tag_id` · `int8` · required
  >
  > Points at [`tag_counts`](#tag_counts).
- 🔤 device_format
  > `series_counts_2.device_format` · `varchar` · optional
- 🧩 hold_source
  > `series_counts_2.hold_source` · `jsonb` · required
- 🔗 license_id <!-- orbit link: "#license_settings", status: "foreign key" -->
  > `series_counts_2.license_id` · `int8` · required
  >
  > Points at [`license_settings`](#license_settings).

### copy_links
<!-- orbit
id: "copy_links"
icon: "🗃️"
collapsed: true
-->
> `copy_links` · table in Maintenance
>
> One row per copy, with 16 columns and about 493 thousand rows.
>
> **Belongs to** [`ebook_requests`](#ebook_requests)

- 🔑 id <!-- orbit status: "primary key" -->
  > `copy_links.id` · `int8` · required
- 🧩 book_title
  > `copy_links.book_title` · `jsonb` · required
- ☑️ audit_status
  > `copy_links.audit_status` · `bool` · optional
- 🔢 hold_position
  > `copy_links.hold_position` · `int4` · optional
- 🔢 member_active
  > `copy_links.member_active` · `int4` · optional
- 🕒 loan_title
  > `copy_links.loan_title` · `timestamp` · required
- 🕒 branch_notes
  > `copy_links.branch_notes` · `timestamp` · optional
- 🕒 visit_archived
  > `copy_links.visit_archived` · `timestamp` · optional
- 🕒 series_updated
  > `copy_links.series_updated` · `timestamp` · optional
- 🔤 review_archived
  > `copy_links.review_archived` · `varchar` · optional
- 🕒 notice_updated
  > `copy_links.notice_updated` · `timestamp` · required
- 🧩 fee_code
  > `copy_links.fee_code` · `jsonb` · optional
- 🔤 member_state
  > `copy_links.member_state` · `varchar` · optional
- 🔗 member_id <!-- orbit link: "#member_counts_2", status: "foreign key" -->
  > `copy_links.member_id` · `int8` · required
  >
  > Points at [`member_counts_2`](#member_counts_2).
- 🕒 copy_state
  > `copy_links.copy_state` · `timestamp` · required
- 🔢 branch_started
  > `copy_links.branch_started` · `int4` · required

### payment_settings
<!-- orbit
id: "payment_settings"
icon: "🧷"
collapsed: true
-->
> `payment_settings` · table in Maintenance
>
> One row per payment, with 23 columns and about 871 thousand rows.
>
> **Belongs to** [`report_links`](#report_links) · [`copy_items`](#copy_items)

- 🔑 id <!-- orbit status: "primary key" -->
  > `payment_settings.id` · `int8` · required
- 🔗 ebook_id <!-- orbit link: "#ebook_requests", status: "foreign key" -->
  > `payment_settings.ebook_id` · `int8` · required
  >
  > Points at [`ebook_requests`](#ebook_requests).
- ☑️ notice_reviewed
  > `payment_settings.notice_reviewed` · `bool` · optional
- 🧩 hold_count
  > `payment_settings.hold_count` · `jsonb` · optional
- 🧩 hold_format
  > `payment_settings.hold_format` · `jsonb` · optional
- 🕒 hold_public
  > `payment_settings.hold_public` · `timestamp` · required
- 🔤 tag_notes
  > `payment_settings.tag_notes` · `varchar` · required
- 🕒 budget_title
  > `payment_settings.budget_title` · `timestamp` · required
- 🔤 report_archived
  > `payment_settings.report_archived` · `varchar` · optional
- 🔢 audit_position
  > `payment_settings.audit_position` · `int4` · optional
- 🔤 shelf_region
  > `payment_settings.shelf_region` · `varchar` · optional
- ☑️ vendor_updated
  > `payment_settings.vendor_updated` · `bool` · required
- 🕒 audit_count
  > `payment_settings.audit_count` · `timestamp` · required
- 🕒 copy_region
  > `payment_settings.copy_region` · `timestamp` · optional
- 🧩 card_ended
  > `payment_settings.card_ended` · `jsonb` · optional
- ☑️ tag_amount
  > `payment_settings.tag_amount` · `bool` · required
- 🧩 audit_notes
  > `payment_settings.audit_notes` · `jsonb` · optional
- 🧩 hold_ended
  > `payment_settings.hold_ended` · `jsonb` · required
- 🧩 branch_code
  > `payment_settings.branch_code` · `jsonb` · optional
- 🕒 visit_notes
  > `payment_settings.visit_notes` · `timestamp` · optional
- 🧩 branch_started
  > `payment_settings.branch_started` · `jsonb` · optional
- 🔗 event_id <!-- orbit link: "#event_history", status: "foreign key" -->
  > `payment_settings.event_id` · `int8` · required
  >
  > Points at [`event_history`](#event_history).
- ☑️ book_position
  > `payment_settings.book_position` · `bool` · optional

### book_links
<!-- orbit
id: "book_links"
icon: "🗂️"
collapsed: true
-->
> `book_links` · table in Maintenance
>
> One row per book, with 14 columns and about 223 thousand rows.
>
> **Belongs to** [`budget_queues`](#budget_queues) · [`series_counts`](#series_counts)

- 🔑 id <!-- orbit status: "primary key" -->
  > `book_links.id` · `int8` · required
- ☑️ copy_archived
  > `book_links.copy_archived` · `bool` · optional
- 🔗 event_id <!-- orbit link: "#event_history", status: "foreign key" -->
  > `book_links.event_id` · `int8` · required
  >
  > Points at [`event_history`](#event_history).
- 🧩 payment_amount
  > `book_links.payment_amount` · `jsonb` · optional
- 🕒 report_title
  > `book_links.report_title` · `timestamp` · required
- 🧩 copy_name
  > `book_links.copy_name` · `jsonb` · required
- 🧩 license_public
  > `book_links.license_public` · `jsonb` · optional
- ☑️ budget_title
  > `book_links.budget_title` · `bool` · required
- 🔤 event_amount
  > `book_links.event_amount` · `varchar` · optional
- 🧩 license_position
  > `book_links.license_position` · `jsonb` · required
- 🧩 repair_priority
  > `book_links.repair_priority` · `jsonb` · required
- 🔤 room_label
  > `book_links.room_label` · `varchar` · required
- 🔢 staff_ended
  > `book_links.staff_ended` · `int4` · optional
- ☑️ role_public
  > `book_links.role_public` · `bool` · optional

