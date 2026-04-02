#!/usr/bin/env python3
import json
import sys
from pathlib import Path

from confluent_kafka import DeserializingConsumer
from confluent_kafka.schema_registry import SchemaRegistryClient
from confluent_kafka.schema_registry.avro import AvroDeserializer
from confluent_kafka.serialization import StringDeserializer


def load_schema() -> str:
    path = Path(__file__).resolve().parent / "avro" / "WindTurbineData.avsc"
    return path.read_text()


def main() -> None:
    schema_str = load_schema()
    schema_registry_conf = {"url": "http://localhost:8081"}
    schema_registry_client = SchemaRegistryClient(schema_registry_conf)

    def dict_to_obj(data, ctx):
        return data

    avro_deserializer = AvroDeserializer(
        schema_registry_client,
        schema_str,
        dict_to_obj,
    )

    # TODO: configure bootstrap.servers, group.id, key/value deserializers, auto.offset.reset
    consumer_conf = {}
    consumer = DeserializingConsumer(consumer_conf)
    topic = "wind-turbine-data-avro"
    consumer.subscribe([topic])

    measurements = {}
    print("Started…")
    try:
        while True:
            msg = consumer.poll(1.0)
            if msg is None:
                continue
            if msg.error():
                print(msg.error())
                continue
            # TODO: read key/value, store in measurements, print records
    except KeyboardInterrupt:
        sys.stderr.write("\nStopped.\n")
    finally:
        consumer.close()


if __name__ == "__main__":
    main()
