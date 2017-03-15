
This document describes steps executed during end to end test; a) first step is to generate the runDesign and post it
to ICS, trigger ICS to run it's (ICS's) version of end to end via ICS's PA simulator and update Run status. b) Once
that is done, SL will run a SAT (Site Acceptance Test) based on PbSmrtPipeScenario, as a first draft.


a) ICS communication with SL Scenario

Following are the steps that get executed in between ICS And SL RunDesignWithICSScenario

1. RunDesignWithICSScenario mimics SL-UI call to SL, where RunDesignWithICSScenario sends a create run design request to SL. Response of
this request is runID.
2. Upon successful creation of RunDesign, RunDesignWithICSScenario retrieves the runDesign so created from SL using runId
3. SL-Sim(RunDesignWithICSScenario) requests ICS to load inventory
4. SL-Sim requests ICS to run RunDesign that is sent to it during this call.
5. SL-Sim requests ICS to run RunRequirements during which ICS will determine if it has enough resources to run this particular RunDesign
6. SL-Sim polls ICS for run status, if the status is Ready, SL-Sim will sleep for a min, allowing ICS to get done with some more intermediate processing
7. SL-Sim, after sleeping for a min, requests ICS to start the run
8. SL-Sim polls ICS for run status, this time we are waiting for Running or Starting
9. SL-Sim sleeps for 15 mins or so, ICS will take this much longer to run and end to end via its PA simulator
10. SL-Sim after 15 mins sleep waits for Complete state



ICSClientApi 

-------------------------
POST /run
description : RunDesign xml is passed onto ICS during this request
input  : 
{ 
    "startedBy" ": "someone",
	"run" : {
		dataModel: "run Design xml".
		uniqueId: "asfasdfasdF"
		summary : ""
	}
}

response : 
{
  "title": "String",
  "type": "string"
} 

--------------------------
POST /run/rqmts
description: After the run tell ICS to scan the inventory with POST /run/rqmts request.  This POST requests scans inventory asynchronously
input : None
response : None; In order to get the response, client needs to poll on GET /run/rqmts, which returns a huge json, from which the only element of interest is 
'hasSufficientInventory'

--------------------------
POST /run/start
description : Tell ICS to start the run ; ICS Starts the processing of run design, this call triggers ICS to communicate with PA and start the run. 
input:  None 
response: None 

---------------------------
POST /test/endtoend/inventory
description : This is endpoint triggers ICS to load inventory, ICS loads a certain script and runs some workflow on its end to do so. 
This is a private end point created by ICS to facilitate end to end testing with SL-Sim. 
input : None
response : None

-----------------------------
GET /instrument/state
description : Used to get run state, DO NOT USE GET /run for getting the run state. Read 'runState' (and not 'state')
input : None
output : 
{
    "dataTransferFreeDiskspace": 117994475520,
    "dataTransferTotalDiskspace": 121626710016,
    "doorLatchState": 1,
    "inServiceMode": false,
    "isLoading": false,
    "latestCollStatsTimestamp": "2017-02-28T18:10:19.351Z",
    "latestRunDataTimestamp": "2017-02-28T18:12:57.983Z",
    "latestSystemAlarmsTimestamp": "2017-02-28T18:10:25.116Z",
    "powerDownRequested": false,
    "runEstimateSecondsRemaining": 0,
    "runName": "1Cell_MB",
    "runRqmtsCheckState": {
        "doneState": {
            "doneTimestamp": "2017-02-28T17:57:26.716Z",
            "exceptionMessage": null,
            "result": 0
        },
        "isChecking": false
    },
    "runState": 9,
    "state": 3
}

---------------------------------
There is implementation for GET /run 
GET /run/rqmts but these endpoints are not used at all. 


b) For SAT test, (reference https://jira.pacificbiosciences.com/browse/SL-953); this is WIP and for the time being it is a work around


1. Take a SubreadSet from PacBioTestData files that has a valid (and mappable) SubreadSet and reference. Update the UUIDs to be consistent with the Run SubreadSet UUID.
2. Run pbvalidate on SubreadSet
3. Import SubreadSet into SL
4. Verify RunQC
5. Create SL SAT job with the reference from PacBioDataSet and the SubreadSet that was imported
6. Verify Successful job and Reports are present
