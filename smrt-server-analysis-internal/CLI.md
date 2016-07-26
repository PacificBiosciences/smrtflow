# Service Command Line Interface to SMRT Link Internal Analysis (SLIA)

exe name `smrt-client-slia`

Subparser Models

### Status

`status` will get the system status

```
$> smrt-client-slia status
-> Summary ServiceStatus(smrtlink_analysis_internal,Services have been up for 15 hours, 55 minutes and 0.533 seconds.,57300533,75bda688-cd66-462c-8909-c60547d586c4,0.1.3-5f1d5fa,secondarytest)
Exiting SLIA 0.1.5 with exitCode 0
```

### Convert

`convert` will convert a Reseq Condition CSV to a Reseq Condition JSON by resolving the required files.


### Submit

`submit` will submit a Reseq Condition CSV to a SLIA instance.



### Misc

Use `pbservice` to get the core functionality (e.g, get status of job) from SMRT Link Analysis.


Example Reseq Condition CSV
 
```
condId,host,jobId
cond_a,smrtlink-beta,17751
cond_b,smrtlink-beta,10159
```

### Todo 

Extend the SMRT Link Analysis Client layer.
