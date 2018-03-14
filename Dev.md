# OSX Local Dev Notes


# Requirements for Unittests

- sbt 0.13.16 (`brew install sbt@0.13`)
- oracle jdk 8 (http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
- Postgres 9.6.x (https://postgresapp.com/documentation/all-versions.html)

## Setup

### DB Setup

Explicitly set `PGDATA` and `PGPORT` in `.bashrc` or `.bash_profile`.  Also add path to psql to PATH

```
export PGPORT=5432
export PGDATA='/Users/mkocher/Library/Application Support/Postgres/var-9.6'
export PATH="/Applications/Postgres.app/Contents/Versions/9.6/bin:$PATH"

```

```
make db-reset-test
make db-reset-prod
```

## Running/Compiling

```
sbt compile
```

Compile tests

```
sbt test:compile
```

Reformatting will happen on compilation.


# Requirements Integration tests

- Python 2.7.13 using `Conda` or `MiniConda` (https://conda.io/miniconda.html)
- Subset of PacBio python repos (defined below)


## Create Conda Env

Create an Env named `smrtflow`

```
conda create --name smrtflow requests cython numpy matplotlib jupyter
```

Activate python env in the current shell

```
source activate smrtflow
```

## Install pysam and ngmlr

```
conda install -c bioconda pysam ngmlr
```

## Better Postgres CLI tool

```
pip install pgcli
```

Pull the following PacBio repos

(using the standard `$USER` env var)

```
git clone http://$USER@bitbucket.nanofluidics.com:7990/scm/sl/chemistry-data-bundle.git
git clone http://$USER@bitbucket.nanofluidics.com:7990/scm/sl/pbcommand.git
git clone http://$USER@bitbucket.nanofluidics.com:7990/scm/sl/pbpipeline-resources.git
git clone http://$USER@bitbucket.nanofluidics.com:7990/scm/sl/pbreports.git
git clone http://$USER@bitbucket.nanofluidics.com:7990/scm/sl/pbsmrtpipe.git
git clone http://$USER@bitbucket.nanofluidics.com:7990/scm/sl/pysiv2.git
git clone http://$USER@bitbucket.nanofluidics.com:7990/scm/sl/siv-tests.git
git clone http://$USER@bitbucket.nanofluidics.com:7990/scm/sl/smrtflow.git
git clone http://$USER@bitbucket.nanofluidics.com:7990/scm/sl/testkit-jobs.git
git clone http://$USER@bitbucket.nanofluidics.com:7990/scm/sat/pbcore.git
git clone http://$USER@bitbucket.nanofluidics.com:7990/scm/sat/pbcoretools.git
git clone http://$USER@bitbucket.nanofluidics.com:7990/scm/sat/pacbiotestdata.git
git clone http://$USER@bitbucket.nanofluidics.com:7990/scm/sat/pacbiotestdata.git
```

```
cd pbcore && pip install . && cd -
cd pbcommand && pip install . && cd -
cd pbsmrtpipe && pip install . && cd -
cd pbcoretools && pip install . && cd -
cd pbreports && pip install  . && cd -
```


## Running Integration tests

Source the conda env, the run the make target

```
source activate smrtflow
make test-int
```




