import re
import networkx as nx
import matplotlib.pyplot as plt

# Function to extract node information from the instance file
def extract_node_info(instance_content):
    node_info = {}
    for line in instance_content:
        if line.strip():
            parts = line.split()
            if len(parts) >= 4 and parts[2].replace('.', '', 1).isdigit() and parts[3].replace('.', '', 1).isdigit():
                node_id, node_type, x, y = parts[0], parts[1], float(parts[2]), float(parts[3])
                node_info[node_id] = {'type': node_type, 'x': x, 'y': y}
    return node_info

# Read the instance file
instance_file_path = "r103_21_25.txt"
with open(instance_file_path, 'r') as file:
    instance_content = file.readlines()

# Extract node information
node_info = extract_node_info(instance_content)

# Using regular expression to find all substrings in the input string
#input_string = "{XIndex@462347=x_C19_D9_S1, XIndex@472ac2=x_C23_D14_S3, XIndex@4c9518f=x_C16_D10_S13, XIndex@4ab1fed=x_D3_C16_S13, XIndex@27fd20=x_D9_C9_S3, XIndex@4ab20a3=x_D8_C22_S13, XIndex@4c8cfc1=x_D11_C2_S15, XIndex@46224a=x_C11_D4_S1, XIndex@4ab14e6=x_C5_C14_S11, XIndex@47ae09=x_D12_C17_S9, XIndex@47ae48=x_D14_C18_S9, XIndex@4aa263b=x_D0_C6_S13, XIndex@464055=x_C22_C7_S8, XIndex@464110=x_C18_C8_S9, XIndex@27f13c=x_C3_D3_S1, XIndex@27ed3b=x_C2_C1_S0, XIndex@4aa2677=x_D2_C4_S13, XIndex@28eeb5=x_D7_C19_S1, XIndex@28fcf6=x_D1_C10_S5, XIndex@4ab2f4b=x_D5_C23_S17, XIndex@4ab2009=x_D4_C13_S13, XIndex@28eb4c=x_C9_C24_S1, XIndex@27f883=x_C1_D6_S3, XIndex@4c9466c=x_C17_D11_S10, XIndex@4aa234f=x_C6_D2_S13, XIndex@471b62=x_C10_C15_S1, XIndex@28ee8f=x_D6_C12_S1, XIndex@efb9e010=x_C12_C21_fictius, XIndex@4c85421=x_C13_C5_S13, XIndex@462669=x_C24_D5_S1, XIndex@4c850b8=x_C25_D0_S11, XIndex@4c95c39=x_C21_D12_S15, XIndex@4c9d0f8=x_D13_C25_S17, XIndex@4c8547a=x_C15_D1_S13, XIndex@462d71=x_C20_D7_S3, XIndex@47973f=x_D10_C11_S3, XIndex@47273b=x_C14_C20_S4, XIndex@4aa2317=x_C4_D8_S13, XIndex@27f55a=x_C7_C3_S2, XIndex@290cd6=x_C8_D13_S9}"
#input_string=" {XIndex@281328=x_D5_C7_S9, XIndex@efb8e6fd=x_C15_C5_fictius, XIndex@ef9baf9d=x_C9_C20_fictius, XIndex@efb8e6bf=x_C12_D5_fictius, XIndex@28f9ef=x_C7_C10_S5, XIndex@4c86ec0=x_C25_D0_S19, XIndex@efb9e3f1=x_C23_C22_fictius, XIndex@efb9e070=x_C16_C14_fictius, XIndex@efb9e033=x_C14_C15_fictius, XIndex@ef9ab551=x_C2_C1_fictius, XIndex@ef9ab597=x_C3_D9_fictius, XIndex@efb9e737=x_C20_D13_fictius, XIndex@4aa263b=x_D0_C6_S13, XIndex@efb8e660=x_C10_C3_fictius, XIndex@4aa2df7=x_D2_C2_S15, XIndex@ef9baf05=x_C4_C23_fictius, XIndex@ef9baf05=x_C5_C13_fictius, XIndex@efb8e6db=x_C13_D2_fictius, XIndex@efb8e75d=x_C18_C8_fictius, XIndex@28ef0d=x_D9_C24_S1, XIndex@efb8ea9e=x_C24_C4_fictius, XIndex@efb9e3d1=x_C22_C21_fictius, XIndex@efb9e394=x_C21_C12_fictius, XIndex@efb9e094=x_C17_C19_fictius, XIndex@47ae29=x_D13_C18_S9, XIndex@4ab1185=x_C8_C17_S10, XIndex@ef9baeaa=x_C1_C25_fictius, XIndex@efb9e0ca=x_C19_C11_fictius, XIndex@ef9baf27=x_C6_C16_fictius, XIndex@efb8e685=x_C11_C9_fictius}"
#input_string="{XIndex@efb8e77a=x_C18_D6_fictius, XIndex@efb8e6fd=x_C15_C5_fictius, XIndex@4ab36ee=x_D6_C25_S19, XIndex@2812ad=x_D1_C8_S9, XIndex@efb9e3f1=x_C23_C22_fictius, XIndex@efb9e070=x_C16_C14_fictius, XIndex@ef9ab551=x_C2_C1_fictius, XIndex@4ab20a4=x_D9_C13_S13, XIndex@ef9baf0a=x_C5_C18_fictius, XIndex@4c94613=x_C14_C15_S11, XIndex@efb8ea24=x_C20_C6_fictius, XIndex@ef9baf05=x_C4_C23_fictius, XIndex@ef9ab53a=x_C1_C9_fictius, XIndex@28eb4c=x_C9_C24_S1, XIndex@28f28c=x_C7_C20_S3, XIndex@efb8ea9e=x_C24_C4_fictius, XIndex@efb8e69e=x_C12_C3_fictius, XIndex@27f140=x_C3_D7_S1, XIndex@efb9e3d1=x_C22_C21_fictius, XIndex@efb9dfd1=x_C11_C10_fictius, XIndex@efb9e015=x_C13_C16_fictius, XIndex@efb9e394=x_C21_C12_fictius, XIndex@efb9e094=x_C17_C19_fictius, XIndex@4ab1185=x_C8_C17_S10, XIndex@463134=x_C10_D9_S5, XIndex@efb9e0ca=x_C19_C11_fictius, XIndex@464473=x_C25_C7_S9, XIndex@ef9ab5ec=x_C6_D1_fictius, XIndex@4aa2e92=x_D7_C2_S15}"

#input_string="{XIndex@efb8e67c=x_C10_D0_fictius, XIndex@efb8e6fd=x_C15_C5_fictius, XIndex@efb9e070=x_C16_C14_fictius, XIndex@efb9e033=x_C14_C15_fictius, XIndex@ef9ab551=x_C2_C1_fictius, XIndex@ef9ab594=x_C3_D6_fictius, XIndex@290cbc=x_D7_C18_S9, XIndex@efb8e6e0=x_C13_D7_fictius, XIndex@4aa2e73=x_D6_C2_S15, XIndex@efb8ea24=x_C20_C6_fictius, XIndex@ef9baf05=x_C4_C23_fictius, XIndex@ef9baf05=x_C5_C13_fictius, XIndex@4c96057=x_C23_C22_S17, XIndex@ef9ab53a=x_C1_C9_fictius, XIndex@28f28c=x_C7_C20_S3, XIndex@efb8e75d=x_C18_C8_fictius, XIndex@efb8ea9e=x_C24_C4_fictius, XIndex@efb8e69e=x_C12_C3_fictius, XIndex@efb9e3d1=x_C22_C21_fictius, XIndex@efb9dfd1=x_C11_C10_fictius, XIndex@efb9e394=x_C21_C12_fictius, XIndex@efb9e094=x_C17_C19_fictius, XIndex@efb9e0ca=x_C19_C11_fictius, XIndex@4c9d83c=x_D11_C25_S19, XIndex@efb8eac0=x_C25_C7_fictius, XIndex@ef9bb2e3=x_C6_D11_fictius, XIndex@ef9bafa1=x_C9_C24_fictius, XIndex@4ab1f90=x_D0_C16_S13, XIndex@ef9baf66=x_C8_C17_fictius}"
#input_string="{XIndex@27f867=x_C1_C9_S3, XIndex@efb8e6fd=x_C15_C5_fictius, XIndex@efb9e3f1=x_C23_C22_fictius, XIndex@efb9e070=x_C16_C14_fictius, XIndex@efb9e033=x_C14_C15_fictius, XIndex@ef9ab591=x_C3_D3_fictius, XIndex@ef9ab551=x_C2_C1_fictius, XIndex@290cfa=x_D9_C18_S9, XIndex@efb8ea24=x_C20_C6_fictius, XIndex@4aa2337=x_C5_D9_S13, XIndex@ef9baf05=x_C4_C23_fictius, XIndex@28f28c=x_C7_C20_S3, XIndex@efb8e75d=x_C18_C8_fictius, XIndex@efb8ea9e=x_C24_C4_fictius, XIndex@efb8e69e=x_C12_C3_fictius, XIndex@efb9e3d1=x_C22_C21_fictius, XIndex@efb9dfd1=x_C11_C10_fictius, XIndex@46312d=x_C10_D2_S5, XIndex@4ab2047=x_C6_D13_S13, XIndex@efb9e015=x_C13_C16_fictius, XIndex@efb9e394=x_C21_C12_fictius, XIndex@efb9e094=x_C17_C19_fictius, XIndex@ef9bb269=x_D2_C13_fictius, XIndex@efb9e0ca=x_C19_C11_fictius, XIndex@4c9d87a=x_D13_C25_S19, XIndex@efb8eac0=x_C25_C7_fictius, XIndex@ef9bafa1=x_C9_C24_fictius, XIndex@4aa2e16=x_D3_C2_S15, XIndex@ef9baf66=x_C8_C17_fictius}"
#input_string="{XIndex@27f867=x_C1_C9_S3, XIndex@efb8e6fd=x_C15_C5_fictius, XIndex@efb9e3f1=x_C23_C22_fictius, XIndex@efb9e070=x_C16_C14_fictius, XIndex@efb9e033=x_C14_C15_fictius, XIndex@ef9ab551=x_C2_C1_fictius, XIndex@47ae48=x_D14_C18_S9, XIndex@ef9bb289=x_C3_D14_fictius, XIndex@ef9bb2c8=x_D4_C25_fictius, XIndex@4aa2332=x_C5_D4_S13, XIndex@efb8ea24=x_C20_C6_fictius, XIndex@ef9baf05=x_C4_C23_fictius, XIndex@28f28c=x_C7_C20_S3, XIndex@efb8e75d=x_C18_C8_fictius, XIndex@efb8ea9e=x_C24_C4_fictius, XIndex@efb8e69e=x_C12_C3_fictius, XIndex@efb9e3d1=x_C22_C21_fictius, XIndex@efb9dfd1=x_C11_C10_fictius, XIndex@4ab2047=x_D6_C13_S13, XIndex@efb9e015=x_C13_C16_fictius, XIndex@efb9e394=x_C21_C12_fictius, XIndex@efb9e094=x_C17_C19_fictius, XIndex@ef9ab5f4=x_C6_D9_fictius, XIndex@efb9e0ca=x_C19_C11_fictius, XIndex@efb8eac0=x_C25_C7_fictius, XIndex@ef9bafa1=x_C9_C24_fictius, XIndex@efb8e682=x_C10_D6_fictius, XIndex@4aa2ed0=x_D9_C2_S15, XIndex@ef9baf66=x_C8_C17_fictius}"
#input_string="{XIndex@281328=x_D5_C7_S9, XIndex@462342=x_C19_D4_S1, XIndex@28f66d=x_D9_C11_S3, XIndex@4c949cf=x_C14_D10_S11, XIndex@28f66f=x_C9_D13_S3, XIndex@46264e=x_C24_C9_S1, XIndex@efb9e0b0=x_C18_C16_fictius, XIndex@4c857c1=x_C21_D3_S13, XIndex@efb9e3b3=x_C22_C12_fictius, XIndex@4c86744=x_C25_D6_S17, XIndex@4ab23e7=x_C4_C21_S15, XIndex@efb9e3f4=x_C23_C25_fictius, XIndex@28f5bb=x_C3_C19_S4, XIndex@4c8c89f=x_D14_C5_S13, XIndex@27f53c=x_D6_C4_S1, XIndex@4c95117=x_C12_D14_S13, XIndex@4ab2009=x_D3_C23_S13, XIndex@4ab2009=x_D4_C13_S13, XIndex@461ea5=x_C13_C1_S0, XIndex@efb9dfd1=x_C11_C10_fictius, XIndex@462d6f=x_C20_D5_S3, XIndex@ef9ab5f1=x_C7_C6_fictius, XIndex@4ab2087=x_D8_C15_S13, XIndex@46966a=x_D10_C3_S1, XIndex@47ae29=x_D13_C18_S9, XIndex@4ab1505=x_C6_C14_S11, XIndex@ef9ab5b5=x_C5_C8_fictius, XIndex@4c84d3c=x_C17_D7_S11, XIndex@463133=x_C10_D8_S5, XIndex@4aa2a5c=x_C2_D9_S15, XIndex@4aa2710=x_D7_C2_S13, XIndex@4c9c1b3=x_D11_C22_S13, XIndex@28f1d2=x_C1_C20_S3, XIndex@4c958f3=x_C15_D11_S15, XIndex@4723bc=x_C16_C24_S3, XIndex@ef9baf66=x_C8_C17_fictius}"
#input_string="{XIndex@27f867=x_C1_C9_S3, XIndex@ef9ab8d8=x_D0_C5_fictius, XIndex@efb8e77b=x_C19_C7_fictius, XIndex@efb9dfba=x_C10_C18_fictius, XIndex@efb9e0b0=x_C18_C16_fictius, XIndex@efb9e070=x_C16_C14_fictius, XIndex@efb9dff0=x_C11_C20_fictius, XIndex@efb9e033=x_C14_C15_fictius, XIndex@efba54b0=x_D14_C24_fictius, XIndex@efb9e3b5=x_C12_D14_fictius, XIndex@efb9e3f4=x_C23_C25_fictius, XIndex@4c8549c=x_C17_C4_S13, XIndex@ef9ab5ce=x_C6_C2_fictius, XIndex@ef9bb28e=x_D3_C19_fictius, XIndex@ef9baec5=x_C2_C21_fictius, XIndex@ef9baec5=x_C3_C11_fictius, XIndex@ef9baf04=x_C4_C22_fictius, XIndex@2908f6=x_C7_C13_S9, XIndex@efb8ead8=x_C25_D0_fictius, XIndex@efb8ea9b=x_C24_C1_fictius, XIndex@efb8ea5e=x_C21_D2_fictius, XIndex@efb9e3d3=x_C22_C23_fictius, XIndex@ef9ab5b5=x_C5_C8_fictius, XIndex@efb9e04f=x_C15_C12_fictius, XIndex@ef9ab62c=x_C9_C3_fictius, XIndex@efb8ea40=x_C20_D3_fictius, XIndex@efb8e6c0=x_C13_C6_fictius, XIndex@ef9baf66=x_C8_C17_fictius, XIndex@28fd15=x_D2_C10_S5}"
#input_string="{XIndex@ef9ab8da=x_D0_C7_fictius, XIndex@efb8e73c=x_C17_C6_fictius, XIndex@efb8e73e=x_C16_D8_fictius, XIndex@efb9e431=x_C25_C24_fictius, XIndex@efb9e3f1=x_C24_C12_fictius, XIndex@efb9e0b1=x_C18_C17_fictius, XIndex@ef9ab9d3=x_D8_C8_fictius, XIndex@ef9ab5d3=x_C5_D7_fictius, XIndex@efba5436=x_D11_C16_fictius, XIndex@ef9baf49=x_C7_C19_fictius, XIndex@ef9ab58e=x_C3_D0_fictius, XIndex@efb9e06e=x_C15_C22_fictius, XIndex@ef9bb340=x_C9_D11_fictius, XIndex@ef9baf07=x_C4_C25_fictius, XIndex@ef9baec5=x_C2_C21_fictius, XIndex@efb8ea27=x_C20_C9_fictius, XIndex@efb8ea5e=x_C22_C2_fictius, XIndex@efb8e69e=x_C12_C3_fictius, XIndex@efb8e6de=x_C14_C5_fictius, XIndex@efb8ea5f=x_C21_D3_fictius, XIndex@efb9dfd1=x_C11_C10_fictius, XIndex@efb9e013=x_C13_C14_fictius, XIndex@efb9e3d5=x_C23_C15_fictius, XIndex@ef9baea8=x_C1_C23_fictius, XIndex@efb9e0ca=x_C19_C11_fictius, XIndex@ef9ab9ee=x_D9_C4_fictius, XIndex@ef9ab9ad=x_D7_C1_fictius, XIndex@ef9baf67=x_C8_C18_fictius, XIndex@efb8e685=x_C10_D9_fictius, XIndex@ef9baf24=x_C6_C13_fictius, XIndex@ef9bb2a4=x_D3_C20_fictius} Initial Objective Function: 2255.038298267485"
input_string="{XIndex@efb8e73c=x_C17_C6_fictius, XIndex@efb8e77c=x_C18_D8_fictius, XIndex@efb8ea7d=x_C23_C2_fictius, XIndex@efb9e431=x_C25_C24_fictius, XIndex@efb9e3f1=x_C24_C12_fictius, XIndex@efb9e070=x_C16_C14_fictius, XIndex@efb9e033=x_C14_C15_fictius, XIndex@efb9e3b2=x_C12_D11_fictius, XIndex@ef9ab5d1=x_C6_C5_fictius, XIndex@ef9baf0a=x_C5_C18_fictius, XIndex@ef9ab64b=x_C9_D3_fictius, XIndex@4c95898=x_C22_C13_S15, XIndex@ef9baf49=x_C7_C19_fictius, XIndex@efb9e06e=x_C15_C22_fictius, XIndex@efb8e6e2=x_C13_D9_fictius, XIndex@ef9baf07=x_C4_C25_fictius, XIndex@ef9baec5=x_C2_C21_fictius, XIndex@efb8ea27=x_C20_C9_fictius, XIndex@ef9ab538=x_C1_C7_fictius, XIndex@efb9dfd1=x_C11_C10_fictius, XIndex@efb9dfd1=x_C10_C20_fictius, XIndex@ef9ab9f2=x_D9_C8_fictius, XIndex@ef9ab933=x_D3_C3_fictius, XIndex@ef9ab570=x_C3_C1_fictius, XIndex@4c9d0b8=x_D11_C23_S17, XIndex@efb9e0ca=x_C19_C11_fictius, XIndex@efb8ea41=x_C21_C4_fictius, XIndex@ef9baf66=x_C8_C17_fictius, XIndex@ef9bb326=x_D8_C16_fictius}"

matches = re.findall(r'=(\w+)', input_string)

if matches:
    result_array = list(matches)

    # Create a list of arcs
    arcs = []
    for i in range(len(result_array)):
        if result_array[i].split('_')[3] == "fictius":
            a = f"{result_array[i].split('_')[1]}->{result_array[i].split('_')[2]}"
            arcs.append(a)
        elif result_array[i].split('_')[3].startswith("S"):
            a1 = f"{result_array[i].split('_')[1]}->{result_array[i].split('_')[3]}"
            a2 = f"{result_array[i].split('_')[3]}->{result_array[i].split('_')[2]}"
            arcs.append(a1)
            arcs.append(a2)

    # Preprocess nodes to collapse only "D" nodes
    unique_nodes = set()
    collapsed_arcs = []

    for arc in arcs:
        source, target = arc.split('->')
        if source.startswith("D"):
            source = "D0"
        if target.startswith("D"):
            target = "D0"
        unique_nodes.add(source)
        unique_nodes.add(target)
        collapsed_arcs.append((source, target))

    # Create a directed graph
    G = nx.DiGraph()

    # Add edges to the graph
    for arc in collapsed_arcs:
        source, target = arc
        G.add_edge(source, target)

    # Draw the graph
    pos = {node: (node_info[node]['x'], node_info[node]['y']) for node in G.nodes}

    node_colors = []

    for node in G.nodes:
        if node.startswith("C"):
            node_colors.append("lightblue")
        elif node.startswith("D"):
            node_colors.append("red")
        elif node.startswith("S"):
            node_colors.append("lightgreen")

    nx.draw(G, pos, with_labels=True, font_weight='bold', node_size=400, node_color=node_colors, font_size=8,
            arrowsize=6)

    # Display the graph
    plt.show()
else:
    print("No substrings found.")
