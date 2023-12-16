import re
import networkx as nx
import matplotlib.pyplot as plt

# Your result data
data = """
Key: XIndex@464055, Value: x_C22_C7_S8
Key: XIndex@464110, Value: x_C18_C8_S9
Key: XIndex@46224a, Value: x_C11_D4_S1
Key: XIndex@4aa263b, Value: x_D0_C6_S13
Key: XIndex@4c9466c, Value: x_C17_D11_S10
Key: XIndex@4aa2677, Value: x_D2_C4_S13
Key: XIndex@462347, Value: x_C19_D9_S1
Key: XIndex@4ab20a3, Value: x_D8_C22_S13
Key: XIndex@4ab2009, Value: x_D4_C13_S13
Key: XIndex@462669, Value: x_C24_D5_S1
Key: XIndex@47273b, Value: x_C14_C20_S4
Key: XIndex@4aa2317, Value: x_C4_D8_S13
Key: XIndex@4aa234f, Value: x_C6_D2_S13
Key: XIndex@472ac2, Value: x_C23_D14_S3
Key: XIndex@4c8cfc1, Value: x_D11_C2_S15
Key: XIndex@28eb4c, Value: x_C9_C24_S1
Key: XIndex@4ab2f4b, Value: x_D5_C23_S17
Key: XIndex@290cd6, Value: x_C8_D13_S9
Key: XIndex@27ed3b, Value: x_C2_C1_S0
Key: XIndex@462d71, Value: x_C20_D7_S3
Key: XIndex@47ae48, Value: x_D14_C18_S9
Key: XIndex@47ae09, Value: x_D12_C17_S9
Key: XIndex@28eeb5, Value: x_D7_C19_S1
Key: XIndex@28ee8f, Value: x_D6_C12_S1
Key: XIndex@efb9e010, Value: x_C12_C21_fictius
Key: XIndex@4ab14e6, Value: x_C5_C14_S11
Key: XIndex@4c8547a, Value: x_C15_D1_S13
Key: XIndex@4c85421, Value: x_C13_C5_S13
Key: XIndex@27f13c, Value: x_C3_D3_S1
Key: XIndex@4c9d0f8, Value: x_D13_C25_S17
Key: XIndex@4c850b8, Value: x_C25_D0_S11
Key: XIndex@4c9518f, Value: x_C16_D10_S13
Key: XIndex@27f55a, Value: x_C7_C3_S2
Key: XIndex@47973f, Value: x_D10_C11_S3
Key: XIndex@27f883, Value: x_C1_D6_S3
Key: XIndex@4c95c39, Value: x_C21_D12_S15
Key: XIndex@471b62, Value: x_C10_C15_S1
Key: XIndex@4ab1fed, Value: x_D3_C16_S13
Key: XIndex@28fcf6, Value: x_D1_C10_S5
Key: XIndex@27fd20, Value: x_D9_C9_S3
"""

# Extract values after 'x_'
pattern = re.compile(r'x_([^\s]+)')

# Extract and store nodes
nodes = [match.group(1).split('_') for match in pattern.finditer(data)]

# Create a directed graph
G = nx.DiGraph()

# Add edges to the graph
for node in nodes:
    if len(node) >= 2:
        G.add_edge(node[0], node[1])

# Plot the graph
pos = nx.spring_layout(G)
nx.draw(G, pos, with_labels=True, font_weight='bold', node_size=150, node_color='skyblue', font_size=6)
plt.title('Graph of Nodes')
plt.show()

# Check if the graph is closed
is_closed = nx.is_strongly_connected(G)

if is_closed:
    print("The graph is closed.")
else:
    print("The graph is not closed.")
