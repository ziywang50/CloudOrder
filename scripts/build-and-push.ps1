param(
  [string]$Region = "us-west-1",
  [string]$AccountId = "000000000000",
  [string]$RepoPrefix = "cloudorder"
)

$services = @{
  apiGateway          = "api-gateway"
  cartService         = "cart-service"
  customerService     = "customer-service"
  eurekaServer        = "eureka-server"
  orderCommandService = "order-command-service"
  OrderQueryService   = "order-query-service"
  productService      = "product-service"
  secKillService      = "seckill-service"
}

$ecrBase = "$AccountId.dkr.ecr.$Region.amazonaws.com"

Write-Host "Logging in to ECR..."
aws ecr get-login-password --region $Region | docker login --username AWS --password-stdin $ecrBase

foreach ($serviceKey in $services.Keys) {
  $repoName = "$RepoPrefix/$($services[$serviceKey])"
  $imageUri = "$ecrBase/$repoName:latest"

  Write-Host "Building $serviceKey -> $imageUri"
  docker build --build-arg SERVICE=$serviceKey -t $imageUri .

  Write-Host "Pushing $imageUri"
  docker push $imageUri
}
