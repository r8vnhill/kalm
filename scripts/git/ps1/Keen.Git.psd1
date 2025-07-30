@{
    <#
     # 🧩 Core Module Metadata
     ###########################>

    # Entry point for the module (loads and initializes functions)
    RootModule        = 'Keen.Git.psm1'

    # Version of the module (semantic versioning recommended)
    ModuleVersion     = '0.3.0'

    # Unique identifier for the module (generated using [guid]::NewGuid())
    GUID              = '30cd722f-ccb0-46a3-a1da-d09194ec5954'

    # Author and ownership information
    Author            = 'Ignacio Slater-Muñoz'
    CompanyName       = 'KEEN-OP'
    Copyright         = '(c) Ignacio Slater-Muñoz. All rights reserved.'

    # Human-readable module description
    Description       = 'Keen.Git provides Git command wrappers for simplified and structured usage in PowerShell.'

    # Minimum required PowerShell version
    PowerShellVersion = '7.0'

    <#
     # 🚀 Exported Components
     ##########################>

    # Public function names (must match function names defined in scripts)
    FunctionsToExport = @(
        'Invoke-GitCheckout',
        'Invoke-GitFetch',
        'Invoke-GitSync'
    )

    # No cmdlets or variables are exported from this module
    CmdletsToExport   = @()
    VariablesToExport = @()

    # Aliases that map to exported functions (user-friendly or shorthand commands)
    AliasesToExport   = @(
        'Git-Checkout',
        'Git-Fetch',
        'Git-Sync'
    )

    <#
     # 📦 Additional Metadata (for publishing/discovery)
     ###################################################>

    PrivateData       = @{
        PSData = @{

            # Keywords to improve discoverability (for PSGallery or documentation)
            Tags       = @(
                'git',
                'checkout',
                'version-control',
                'powershell',
                'automation',
                'cli',
                'fetch',
                'sync'
            )

            # Licensing and source information
            LicenseUri = 'https://opensource.org/licenses/BSD-2-Clause'
            ProjectUri = 'https://gitlab.com/r8vnhill/keen-op'
        }
    }
}
