#!/usr/bin/env python3
import sys
from pathlib import Path

from confluent_kafka import SerializingProducer
from confluent_kafka.schema_registry import SchemaRegistryClient
from confluent_kafka.schema_registry.avro import AvroSerializer
from confluent_kafka.serialization import StringSerializer

from wind_turbine_data_supplier import WindTurbineDataSupplier


def load_schema() -> str:
    path = Path(__file__).resolve().parent / "avro" / "WindTurbineData.avsc"
    return path.read_text()


def main() -> None:
    msgs_per_sec = 1
    if len(sys.argv) > 1:
        msgs_per_sec = int(sys.argv[1])

    schema_str = load_schema()
    schema_registry_conf = {"url": "http://localhost:8081"}
    schema_registry_client = SchemaRegistryClient(schema_registry_conf)

    def to_dict(obj, ctx):
        return obj

    avro_serializer = AvroSerializer(
        schema_registry_client,
        schema_str,
        to_dict,
        conf={"auto.register.schemas": True},
    )

    # TODO: fill producer_conf with bootstrap.servers, key.serializer, value.serializer
    producer_conf = {}
    producer = SerializingProducer(producer_conf)
    topic = "wind-turbine-data-avro"

    try:
        for data in WindTurbineDataSupplier(50, msgs_per_sec):
            # TODO: produce to topic with key = windTurbineId, value = data dict
            print(f"Produced data for wind turbine {data['windTurbineId']}")
    finally:
        producer.flush()


if __name__ == "__main__":
    main()
