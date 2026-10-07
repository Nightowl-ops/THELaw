

Project 2:

Digital Evidence Vault & Chain-of-Custody Managment System

1. users

id: PK

full_name

email

password_hash

role

status

profile_picture_url

is_email_verified

verification_token

verification_token_expiry

password_reset_token

password_reset_token_expiry

created_at

updated_at

2. legal_cases

id: PK

case_number

case_title

case_type

lead_attorney_id: FK

client_id: FK

opposing_party_name

opposing_counsel_name

opposing_counsel_email

status

created_at

updated_at

3. evidence_items

id: PK

case_id: FK

item_tracking_code

name

description

evidence_type

source_origin

seizing_officer_name

officer_badge_number

law_enforcement_agency

police_incident_report_num

storage_url

file_size_bytes

sha256_hash

status

uploaded_by_user_id: FK

created_at

updated_at

4. chain_of_custody_logs

id: PK

evidence_id: FK

actor_user_id: FK

action_type

notes

hash_at_event

logged_at

5. custody_reservations

id: PK

evidence_id: FK

examiner_id: FK

start_time

end_time

status

notes

created_at

updated_at

6. exhibit_binders

id: PK

case_id: FK

binder_name

bates_prefix

status

created_by_user_id: FK

created_at

updated_at

7. binder_exhibits

id: PK

binder_id: FK

evidence_id: FK

exhibit_number

bates_start_num

bates_end_num

added_at

8. discovery_productions

id: PK

case_id: FK

binder_id: FK

target_party_name

access_token

download_password_hash

is_watermarked

expires_at

created_by_user_id: FK

created_at

9. discovery_access_logs

id: PK

production_id: FK

ip_address

user_agent

downloaded_file_name

accessed_at




1.) Users--> legal cases

one user can have many legal cases


for example :

legal_cases.lead_attorney_id --> user.id

legal_cases.client_id --> users.id

one lead attorny can manage many legal cases

one client can have one or more legal cases assigned to them


why the reason for the users type to authorize who can read and who can edit and upload accordingly


2.) legal_cases --> evidence_items(one to many)

Foreign key: evidnece_items.case_id --> legal_cases.id

one legal case contains many evidence items each peice of evidence belongs to one case
many evidence to a case connected throught the case id to connected to the evidenct item.case id



STILL UNDERCOSIDERATIONS!!!



anything uploaded to the evidence depends on the user type for example a clinet may have limited upload capacity and some may have other privilages still under consideration






3.) evidence_items --> chain_of_custody_logs

one to many relationship

chain_id --> evidence_id


one piece of evidence accumulates many chronological log entires over time .

this piece of evidence can be used for checking again and again reexamine and to be used so the many happens here


4.) evidence_items --> custody_reservations

custody_id--> evidence_id

custody_reservations.examiner_id --> users.id

one piece of evidence can have multiple scheduled checkout reservations over its lifetime
example bookekd for analysis or booked for another thing in another day and so on




5.) Legal_cases --> exhibit binders

one to many relationship

one case can have multiple exhibits created for different hearings

example : a single lawsuit might require a bail hearing  binder a motion to suppress binder and final trial binder
all of these belong to the same case


6.) exhibit_binder --> evidence_items via binder_exhibits

FK binder_exhibits:

binder_exhibits.binder_id --> exhibit_binders.id

binder_exhibits.evidence_id --> evidence_items.id

many_to_many

why is it important it holds join table .it holds extra metadata specific


7.) exhibit_binders--> discorvery_production

FK

binder_exhibits.binder_id --> exhibit_binders.id

binder_exhibits.evidence_id --> evidence_items.id

relationship one finalized binder can be packaged into one or more discovery production sent to externak parties.

why it matters : when an attorny finalizes an exhibit binder, they create a production package it generates an expiring URL token for the opposing counsel to download those exhibits without giving them an internal login account


8.) discovery_productions --> discovery_access_logs

FK : discovery_access_logs.production_id --> discovery_profduction.id

Relationship: one discovery production package can have multiple download events logged against it

why it matters:
every time the opposing counsel clicks the download link. a new row inserted into this table recording their IP address 



Roles and UserStatus

Roles.java and UserStatus.java

in our database schema a user cant just be anything .they must be one of four specific roles and there status active and inactive

For the soft delete part we dont want to delete any user from the system because of missing record and name form the databse would cause problems

so we inactivate it

there logs remain in there but the user is incative
solved the soft delete by the roles 


Configurations File

This is our master configuration file

this contains settings common to all enviroments and tells spring which active profile to load

1.)application.yml


this is used to for running the application locally


2.)application-dev.yml


to check the working of the logic
without touching our actual database correct.

3.)application-test.yml



login and verification

JWT (JSON Web Token)

lets think of JWT authentication like getting an entry card to an exclusive event.

1.) login(show id): you present you email and password to the front desk
which in our code is (/auth/users/login).

2.) Verification & issuance : the desk confirms your identity against the database, stamps unforgettable ,tamper proof writsband with your identity and roles  JWT String

3.) Subsequent Visits: you dont need to reverify each time you do a request you use you card to verify you dont need to reenter your password Authorization : Bearer,<token>

4.) Door Security Check :we check the wristband no expired not tampered with if not 401 will appear






