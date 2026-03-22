<template>
  <div class="chat-layout-shell">
    <main class="chat-main">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
.chat-layout-shell {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  padding: 20px;
}

.chat-main {
  min-width: 0;
  flex: 1;
}
</style>
