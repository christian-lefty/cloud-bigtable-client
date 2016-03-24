# Bigtable Protos

## Purpose
This project houses the raw .proto files that are used for accessing Google Cloud Bigtable.  This project generates a jar with java artifacts derived from the proto files in this project.  These proto files can also be used to generate other clients.

This project reliese on artifacts from https://github.com/grpc/grpc-java. You can see more about gRPC at https://github.com/grpc/grpc-common.

## Bigtable proto files

There are 3 types of functionality that the protos support:

* Table administration - creating, modifying and deleting Bigtable tables
* Table data manipulation
* Cluster administration - creating, modifying and delting Bigtable clusters, which are a collection of Tables.

Here are the files directly associated with Bigtable functionality 
* src/main/proto/google/bigtable/admin/table/v1approved/bigtable_table_data.proto
* src/main/proto/google/bigtable/admin/table/v1approved/bigtable_table_service.proto
* src/main/proto/google/bigtable/admin/table/v1approved/bigtable_table_service_messages.proto
* src/main/proto/google/bigtable/v1approved/bigtable_data.proto
* src/main/proto/google/bigtable/v1approved/bigtable_service.proto
* src/main/proto/google/bigtable/v1approved/bigtable_service_messages.proto
* src/main/proto/google/bigtable/admin/cluster/v1/bigtable_cluster_data.proto
* src/main/proto/google/bigtable/admin/cluster/v1/bigtable_cluster_service.proto
* src/main/proto/google/bigtable/admin/cluster/v1/bigtable_cluster_service_messages.proto

Copied Protos
--------------

There were some proto files copied into this project from other projects.

Retrieved from https://github.com/google/googleapis:

* src/main/proto/google/api/annotations.proto
* src/main/proto/google/api/http.proto
* src/main/proto/google/longrunning/operations.proto
* src/main/proto/google/protobuf/any.proto
* src/main/proto/google/protobuf/api.proto
* src/main/proto/google/protobuf/duration.proto
* src/main/proto/google/protobuf/empty.proto
* src/main/proto/google/protobuf/field_mask.proto
* src/main/proto/google/protobuf/source_context.proto
* src/main/proto/google/protobuf/timestamp.proto
* src/main/proto/google/protobuf/type.proto
* src/main/proto/google/protobuf/wrappers.proto
* src/main/proto/google/rpc/code.proto
* src/main/proto/google/rpc/error_details.proto
* src/main/proto/google/rpc/status.proto
* src/main/proto/google/type/color.proto
* src/main/proto/google/type/date.proto
* src/main/proto/google/type/dayofweek.proto
* src/main/proto/google/type/timeofday.proto

Retrieved from https://github.com/google/protobuf.git
* src/main/proto/google/protobuf/descriptor.proto

# Generating the java gRPC implementations

The gRPC Java generation process requires protoc 3
to create the Java artifacts and gradle to drive the process.

Protoc can be installed via the instructions at:
https://github.com/grpc/grpc-java#build-protobuf.
Installing it isn't always trivial;
you may have to Google error messages to understand what to install next;
there are plenty of answers on StackOverflow
for protobuf installation requirements.  

Run the following:

```
mvn -P generateProtos package
mvn -P generateServices package
```

# Generating the Python protos

Just like with the Java implementation, you'll need protoc version 3 to compile
these protos (check this with `protoc --version`).

After that, run the following:

```bash
$ cd bigtable-protos
$ mkdir -p src/generated/python
$ cd src/main/proto
$ protoc google/bigtable/v1/*.proto --python_out=../../generated/python
```

Your generated files (`*_pb2.py`) should be inside `src/generated/python`.
