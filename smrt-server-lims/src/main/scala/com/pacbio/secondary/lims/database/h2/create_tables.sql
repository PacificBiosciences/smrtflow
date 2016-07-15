CREATE TABLE IF NOT EXISTS LIMS_YML (
  id INT AUTO_INCREMENT, -- arbitrary primary ket ID
  expcode INT,
  runcode VARCHAR,
  path VARCHAR,
  user VARCHAR,
  uid VARCHAR,
  tracefile VARCHAR,
  description VARCHAR,
  wellname VARCHAR,
  cellbarcode VARCHAR,
  seqkitbarcode VARCHAR,
  cellindex VARCHAR,
  colnum VARCHAR,
  samplename VARCHAR,
  instid INT,
  PRIMARY KEY (expcode, runcode)
);
-- this exists only in this service. arbitrary aliases or short codes
CREATE TABLE IF NOT EXISTS ALIAS (
  alias VARCHAR PRIMARY KEY,
  lims_yml_id INT
);
-- two indexes to support the queries that PK indexes don't cover
CREATE INDEX IF NOT EXISTS index_limsyml_index ON LIMS_YML(ID);
CREATE INDEX IF NOT EXISTS index_limsyml_runcode ON LIMS_YML(RUNCODE);