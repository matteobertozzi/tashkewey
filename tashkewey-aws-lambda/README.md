# Tashkeway on AWS Lambda

Build the docker image to use with the lambda function.
```docker
FROM public.ecr.aws/lambda/java:21

# Copy tashkewey jars, services jars and config
COPY tashkewey-aws-lambda/target/lib/* ${LAMBDA_TASK_ROOT}/lib/
COPY tashkewey-aws-lambda/target/tashkewey-aws-lambda-0.1.0-SNAPSHOT.jar ${LAMBDA_TASK_ROOT}/lib
COPY demo-service/target/demo-service-0.1.0.jar  ${LAMBDA_TASK_ROOT}/lib/
COPY config.json ${LAMBDA_TASK_ROOT}/config.json

# Set the lambda handler (API Gateway, ALB, SQS)
CMD [ "io.github.matteobertozzi.tashkewey.aws.lambda.LambdaApiGatewayEvent::handleRequest" ]
#CMD [ "io.github.matteobertozzi.tashkewey.aws.lambda.LambdaApplicationLoadBalancerEvent::handleRequest" ]
#CMD [ "io.github.matteobertozzi.tashkewey.aws.lambda.LambdaSqsEvent::handleRequest" ]
```