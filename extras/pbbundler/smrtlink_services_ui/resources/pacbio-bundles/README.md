# PacBio Bundles go here

**NOTE** The default **chemistry bundle** needs to be build from artifacts from bamboo or pulled from a git repo. The default chemistry bundle is hardcoded until the location of this resource has been agreed upon. 

- Chemistry related bundle [POC example](http://bitbucket.nanofluidics.com:7990/users/mkocher/repos/chemistry-bundle/browse)
- SL pipeline resources bundle [hello-world example](https://github.com/PacificBiosciences/pbpipeline-helloworld-resources)

Each bundle must have a "manifest.xml" file in it.

```
<?xml version='1.0' encoding='utf-8'?>
<Manifest>
    <Package>example</Package>
    <Version>4.0.0+188835</Version>
    <Created>11/28/16 11:16:46 PM</Created>
    <Author>mpkocher</Author>
</Manifest>
```

### Fields

- bundle type id "Package" [String] globally unique bundle type id (e.g., "chemistry", "pbpipelines.helloworld")
- bundle version "Version" [String] SemVer format
- bundle created at "CreatedAt" [String] ISO8601 timestamp when the bundle was created at
- bundle creator "Author" [String] user that created the bundle
 
A tuple of (bundle type id, version) are the unique identifier used internally.  