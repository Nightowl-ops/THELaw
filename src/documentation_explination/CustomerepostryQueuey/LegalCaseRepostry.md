@Querey are used to customize Sql commands 

Spring Data JPA is smart you can use it for simple things 
but what if search bar with multiple optional filter on you frontend screen 

The custome querey we did is used for filter search in the frontend 

for example a user they want to see everthing or choose only active cases 

Query 1: searchCases (For Law Firm Staff)

Who uses it: Admins, Attorneys, Forensic Examiners.
It searches across all cases in the entire law firm.

Query 2: searchCasesByClient (For Clients)

Who uses it: Logged-in Clients.

Why this is critical: Even if a client types "%" in the 
search bar to try to see everything, the database query 
says: WHERE c.client.id = 5 AND ... PostgreSQL will never
return a case that doesn't belong to client #5. This security
rule is enforced directly inside the database query itself.

simply :

No matter what trick a client types into the search box, the 
database treats their client ID as an unbreakable wall. They
can only search within their own files.