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