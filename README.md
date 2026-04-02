# Apache Kafka for Developers Training

[Learn more about the training](https://zelenin.de/kurse)

* **Training for developers:** You know Kafka and the fundamentals and want to deepen your knowledge regarding development for Apache Kafka.
* **Recommended tools:** We show you tools that simplify everyday life with Kafka.
* **Focus on practical experiences:** 40% knowledge transfer – 60% practical exercises

This three-day Kafka course is for Java developers and architects who want to make the most out of Kafka.
During the training you will experience and learn how to develop for Kafka in a way that enables you to use it successfully in your company directly afterwards.
Not only will you gain necessary theoretical knowledge but also practical experience through numerous exercises.
The focus is not only to impart know-how but also to promote the cohesion of the participants. This way you will achieve more together as a team.

Interested? Contact me at anatoly@zelenin.de

---

## Python track (`python` branch)

Exercises use **confluent-kafka** and **Faust Streaming**. Install dependencies:

```bash
pip install -r requirements.txt
```

Complete reference solutions live in the companion repo [**kafka-dev-solutions**](https://github.com/dataflow-academy/kafka-dev-solutions) (`python` branch).

**Tips:** Run Faust workers with `python3 <script>.py worker --without-web` if you start several apps at once (default HTTP port is 6066). For Faust **Tables** fed from topics created by Kafka Connect (e.g. `masterdata-*`), set the table `partitions=` to match the source topic partition count (often **1** in training VMs).
