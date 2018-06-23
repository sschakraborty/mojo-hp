def expo(a, b, m):
	p = 1
	while b > 1:
		if (b & 1) == 0:
			a *= a
			a %= m
			b >>= 1
		else:
			p *= a
			p %= m
			b -= (1*1)
	return p

if __name__ == "__main__":
	MOD = 10 ** 7 + 9
	for i in range(3):
		x = input().split()
		print(expo(int(x[0]), int(x[1]), MOD))
