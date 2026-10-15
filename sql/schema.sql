-- Run once against your Azure SQL database (Query editor in the portal, SSMS or Azure Data Studio)
IF OBJECT_ID('dbo.users', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.users (
        id            INT IDENTITY(1,1) PRIMARY KEY,
        full_name     NVARCHAR(100) NOT NULL,
        email_address NVARCHAR(150) NOT NULL,
        city          NVARCHAR(100) NOT NULL,
        created_at    DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME()
    );
END
GO

-- Let the App Service's managed identity read/write.
-- Replace <your-app-service-name> with the web app name (the identity has the same name).
-- You must be connected as the server's Microsoft Entra admin to run this.
CREATE USER [<your-app-service-name>] FROM EXTERNAL PROVIDER;
ALTER ROLE db_datareader ADD MEMBER [<your-app-service-name>];
ALTER ROLE db_datawriter ADD MEMBER [<your-app-service-name>];
GO
