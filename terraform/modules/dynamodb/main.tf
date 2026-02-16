resource "aws_dynamodb_table" "shopping_carts" {
  name           = "shopping-carts"
  billing_mode   = "PAY_PER_REQUEST"
  hash_key       = "cart_id"

  attribute {
    name = "cart_id"
    type = "S"
  }

  tags = {
    Name = "hw8-shopping-carts"
  }
}

output "table_name" {
  value = aws_dynamodb_table.shopping_carts.name
}