CREATE KEYSPACE ferrit WITH REPLICATION = {'class' : 'SimpleStrategy', 'replication_factor': 3};
USE ferrit;

CREATE TABLE crawler (
  crawler_id varchar,
  config_json varchar,
  PRIMARY KEY (crawler_id)
);

CREATE TABLE document_metadata (
  crawler_id varchar,
  job_id varchar,
  uri varchar,
  content_type varchar,
  content_length int,
  depth int,
  fetched timestamp,
  response_status varchar,
  PRIMARY KEY (job_id, uri)
);

CREATE TABLE document (
  crawler_id varchar,
  job_id varchar,
  uri varchar,
  content_type varchar,
  content blob,
  PRIMARY KEY (job_id, uri)
);

CREATE TABLE crawl_job_by_crawler (
  crawler_id varchar,
  crawler_name varchar,
  job_id varchar,
  node varchar,
  partition_date DATE,
  snapshot_date timestamp,
  created_date timestamp,
  finished_date timestamp,
  duration bigint,
  outcome varchar,
  message varchar,
  uris_seen int,
  uris_queued int,
  fetch_counters map<varchar,int>,
  response_counters map<varchar,int>,
  media_counters map<varchar,varchar>,
  PRIMARY KEY (crawler_id, job_id)
);

CREATE TABLE crawl_job_by_date (
  crawler_id varchar,
  crawler_name varchar,
  job_id varchar,
  node varchar,
  partition_date DATE,
  snapshot_date timestamp,
  created_date timestamp,
  finished_date timestamp,
  duration bigint,
  outcome varchar,
  message varchar,
  uris_seen int,
  uris_queued int,
  fetch_counters map<varchar,int>,
  response_counters map<varchar,int>,
  media_counters map<varchar,varchar>,
  PRIMARY KEY (partition_date, created_date, job_id)
) WITH CLUSTERING ORDER BY (created_date DESC);


CREATE TABLE fetch_log (
  crawler_id varchar,
  job_id varchar,
  log_time timestamp,
  uri varchar,
  uri_depth int,
  status_code int,
  content_type varchar,
  content_length int,
  links_extracted int,
  fetch_duration int,
  request_duration int,
  parse_duration int,
  uris_seen int,
  uris_queued int,
  fetches int,
  PRIMARY KEY (job_id, log_time, uri)
) WITH CLUSTERING ORDER BY (log_time DESC);
