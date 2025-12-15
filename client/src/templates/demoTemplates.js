/**
 * Demo Code Templates for Code Migration System
 * Demonstrates both Weak and Strong Mobility
 */

export const demoTemplates = {
  // ============================================================
  // STRONG MOBILITY DEMOS (State Preservation)
  // ============================================================

  fibonacciAccumulator: {
    name: "FibonacciAccumulator",
    entryPoint: "main",
    type: "STRONG",
    description: "Tính dãy Fibonacci tích lũy qua nhiều nodes - Demo STRONG mobility",
    code: `// FIBONACCI ACCUMULATOR - STRONG MOBILITY
// Tính Fibonacci tích lũy qua nhiều nodes
// Mỗi node tính 5 số tiếp theo và lưu state
// State: results (array), currentN (số thứ tự)

def main() {
    println "=========================================="
    println "FIBONACCI ACCUMULATOR - STRONG MOBILITY"
    println "=========================================="

    // Initialize state (only first run)
    if (!binding.hasVariable('results')) {
        results = []
        println "[INIT] First run - Initializing state"
    } else {
        println "[RESTORED] State from previous node"
        println "Already calculated: \${results.size()} numbers"
    }

    if (!binding.hasVariable('currentN')) {
        currentN = 0
    }

    println ""
    println "Running on: \${nodeId}"
    println "Starting from: Fib(\${currentN})"
    println ""

    // Calculate next 5 Fibonacci numbers
    def COUNT = 5
    for (int i = 0; i < COUNT; i++) {
        def fib = fibonacci(currentN)
        results << fib
        println "  [\${currentN}] Fib(\${currentN}) = \${fib}"
        currentN++
    }

    println ""
    println "Completed on node: \${nodeId}"
    println "Total calculated: \${results.size()} numbers"
    println "Full sequence: \${results}"
    println "Next will be: Fib(\${currentN})"
    println "=========================================="

    return results
}

def fibonacci(int n) {
    if (n <= 1) return n
    def a = 0, b = 1
    for (int i = 2; i <= n; i++) {
        def temp = a + b
        a = b
        b = temp
    }
    return b
}

main()`,
  },

  statefulCounter: {
    name: "StatefulCounter",
    entryPoint: "main",
    type: "STRONG",
    description: "Đếm số tích lũy qua nhiều nodes - Demo STRONG mobility đơn giản",
    code: `// STATEFUL COUNTER - STRONG MOBILITY
// Đếm số tích lũy qua nodes
// Mỗi node đếm thêm 10 số

def main() {
    println "=========================================="
    println "STATEFUL COUNTER - STRONG MOBILITY"
    println "=========================================="

    // Initialize counter
    if (!binding.hasVariable('counter')) {
        counter = 0
        visitedNodes = []
        println "[INIT] Counter initialized to 0"
    } else {
        println "[RESTORED] Previous counter: \${counter}"
        println "Previous nodes: \${visitedNodes}"
    }

    println ""
    println "Current node: \${nodeId}"
    visitedNodes << nodeId

    println "Counting from \${counter + 1} to \${counter + 10}:"

    // Count 10 times
    for (int i = 1; i <= 10; i++) {
        counter++
        println "  [\${counter}] Count on \${nodeId}"
    }

    println ""
    println "Current counter: \${counter}"
    println "Migration path: \${visitedNodes.join(' -> ')}"
    println "=========================================="

    return counter
}

main()`,
  },

  dataCollector: {
    name: "DataCollector",
    entryPoint: "main",
    type: "STRONG",
    description: "Thu thập dữ liệu từ nhiều nodes - Demo STRONG mobility",
    code: `// DATA COLLECTOR - STRONG MOBILITY
// Thu thập metrics từ nhiều nodes

def main() {
    println "=========================================="
    println "DATA COLLECTOR - STRONG MOBILITY"
    println "=========================================="

    // Initialize collection
    if (!binding.hasVariable('collectedData')) {
        collectedData = []
        startTime = System.currentTimeMillis()
        println "[INIT] Starting data collection"
    } else {
        println "[RESTORED] Previously collected: \${collectedData.size()} records"
    }

    println ""
    println "Collecting from: \${nodeId}"

    // Collect data
    def record = [
        nodeId: nodeId,
        timestamp: System.currentTimeMillis(),
        cpuLoad: Math.random() * 100,
        memoryUsage: Math.random() * 100
    ]

    collectedData << record

    println "CPU Load: \${String.format('%.2f', record.cpuLoad)}%"
    println "Memory: \${String.format('%.2f', record.memoryUsage)}%"
    println ""
    println "Total records: \${collectedData.size()}"
    println "Nodes visited: \${collectedData.collect{it.nodeId}.join(' -> ')}"
    println "=========================================="

    return collectedData
}

main()`,
  },

  // ============================================================
  // WEAK MOBILITY DEMOS (No State - Fresh Start)
  // ============================================================

  helloWorld: {
    name: "HelloWorld",
    entryPoint: "main",
    type: "WEAK",
    description: "Simple Hello World - Demo WEAK mobility",
    code: `// HELLO WORLD - WEAK MOBILITY
// Simple greeting from current node
// Each migration starts fresh

def main() {
    println "=========================================="
    println "HELLO WORLD - WEAK MOBILITY"
    println "=========================================="
    println ""
    println "Hello from node: \${nodeId}"
    println "Timestamp: \${new Date()}"
    println ""
    println "[WEAK] Fresh execution - no state"
    println "After migration, will restart with new nodeId"
    println "=========================================="

    return "Greetings from \${nodeId}"
}

main()`,
  },

  primeChecker: {
    name: "PrimeChecker",
    entryPoint: "main",
    type: "WEAK",
    description: "Kiểm tra số nguyên tố - Demo WEAK mobility",
    code: `// PRIME CHECKER - WEAK MOBILITY
// Check prime numbers from 1 to 50

def main() {
    println "=========================================="
    println "PRIME CHECKER - WEAK MOBILITY"
    println "=========================================="
    println ""
    println "Running on: \${nodeId}"
    println "Checking primes from 1 to 50"
    println ""

    def primes = []
    for (int i = 1; i <= 50; i++) {
        if (isPrime(i)) {
            primes << i
        }
    }

    println "Found \${primes.size()} primes:"
    println primes
    println ""
    println "[WEAK] After migration, will recalculate"
    println "=========================================="

    return primes
}

def isPrime(int n) {
    if (n < 2) return false
    if (n == 2) return true
    if (n % 2 == 0) return false
    for (int i = 3; i * i <= n; i += 2) {
        if (n % i == 0) return false
    }
    return true
}

main()`,
  },

  factorialCalculator: {
    name: "FactorialCalculator",
    entryPoint: "main",
    type: "WEAK",
    description: "Tính giai thừa - Demo WEAK mobility",
    code: `// FACTORIAL CALCULATOR - WEAK MOBILITY
// Calculate factorials from 1! to 10!

def main() {
    println "=========================================="
    println "FACTORIAL CALCULATOR - WEAK MOBILITY"
    println "=========================================="
    println ""
    println "Calculating on: \${nodeId}"
    println ""

    def results = [:]
    for (int i = 1; i <= 10; i++) {
        def fact = factorial(i)
        results[i] = fact
        println "\${i}! = \${fact}"
    }

    println ""
    println "[WEAK] Will recalculate on migration"
    println "=========================================="

    return results
}

def factorial(int n) {
    if (n <= 1) return 1
    long result = 1
    for (int i = 2; i <= n; i++) {
        result *= i
    }
    return result
}

main()`,
  },

  randomDataGenerator: {
    name: "RandomDataGenerator",
    entryPoint: "main",
    type: "WEAK",
    description: "Tạo dữ liệu ngẫu nhiên - Demo WEAK mobility",
    code: `// RANDOM DATA GENERATOR - WEAK MOBILITY
// Generate random samples

def main() {
    println "=========================================="
    println "RANDOM DATA GENERATOR - WEAK MOBILITY"
    println "=========================================="
    println ""
    println "Generating on: \${nodeId}"
    println "Timestamp: \${new Date()}"
    println ""

    def data = []
    println "Generating 5 random samples:"
    for (int i = 1; i <= 5; i++) {
        def sample = [
            id: i,
            value: Math.random() * 100,
            timestamp: System.currentTimeMillis()
        ]
        data << sample
        println "  [\${i}] Value: \${String.format('%.2f', sample.value)}"
    }

    def avg = data.collect{it.value}.sum() / data.size()

    println ""
    println "Average: \${String.format('%.2f', avg)}"
    println ""
    println "[WEAK] New random data after migration"
    println "=========================================="

    return data
}

main()`,
  },
};

// Default template
export const defaultTemplate = demoTemplates.fibonacciAccumulator;

// Categories for UI dropdown
export const templateCategories = {
  strong: {
    label: "STRONG Mobility (State Preserved)",
    templates: [
      'fibonacciAccumulator',
      'statefulCounter',
      'dataCollector',
    ],
  },
  weak: {
    label: "WEAK Mobility (Fresh Start)",
    templates: [
      'helloWorld',
      'primeChecker',
      'factorialCalculator',
      'randomDataGenerator',
    ],
  },
};
